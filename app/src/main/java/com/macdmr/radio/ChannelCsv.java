/* SignalDeck - GPL-3.0-or-later. Modified P8 standard channel CSV v1. */
package com.macdmr.radio;

import com.macdmr.radio.dsp.ToneTable;
import java.math.BigDecimal;
import java.util.*;

/** Shared import/export contract for the Android channel book and a future CPS. No radio I/O. */
public final class ChannelCsv {
    private ChannelCsv(){}
    public static final int MAX_CHARS=2_000_000, MAX_ROWS=5000;
    public static final String HEADER="format_version,name,rx_mhz,tx_mhz,mode,bandwidth_khz,rx_tone,tx_tone,squelch,power,receive_only,zones,scan_lists,colour_code,rx_slot,tx_slot,talkgroup,contact_type,rx_groups,dmr_mode,relay,interrupt,soft_squelch,soft_squelch_level,notes";
    public static final class Row {
        public int line;
        public ChannelStore.Channel channel;
        public List<String> zones=new ArrayList<>(),scanLists=new ArrayList<>();
    }
    public static final class Parsed {
        public final List<Row> rows=new ArrayList<>();
        public final List<String> errors=new ArrayList<>();
        public boolean valid(){return errors.isEmpty();}
    }
    private static final class Record {int line;List<String> cells;Record(int l,List<String> c){line=l;cells=c;}}
    /** RFC4180-style quoted commas, quotes and embedded newlines; strict on malformed quoting. */
    private static List<Record> records(String text){
        if(text==null || text.length()>MAX_CHARS)throw new IllegalArgumentException("CSV is missing or exceeds 2 MB");
        if(text.startsWith("\uFEFF"))text=text.substring(1);
        List<Record> out=new ArrayList<>();List<String> cells=new ArrayList<>();StringBuilder cell=new StringBuilder();
        boolean quoted=false,closed=false;int line=1,start=1;
        for(int i=0;i<text.length();i++){
            char ch=text.charAt(i);
            if(quoted){if(ch=='"'){if(i+1<text.length() && text.charAt(i+1)=='"'){cell.append('"');i++;}else{quoted=false;closed=true;}}
                else{cell.append(ch);if(ch=='\n')line++;}continue;}
            if(ch=='"'){if(closed || cell.length()!=0)throw new IllegalArgumentException("Line "+line+": unexpected quote");quoted=true;continue;}
            if(ch==',' || ch=='\r' || ch=='\n'){
                cells.add(cell.toString());cell.setLength(0);closed=false;
                if(ch!=','){
                    out.add(new Record(start,cells));if(out.size()>MAX_ROWS+1)throw new IllegalArgumentException("CSV exceeds 5000 channel rows");
                    cells=new ArrayList<>();if(ch=='\r' && i+1<text.length() && text.charAt(i+1)=='\n')i++;line++;start=line;
                }
            }else{if(closed)throw new IllegalArgumentException("Line "+line+": text after closing quote");cell.append(ch);}
        }
        if(quoted)throw new IllegalArgumentException("Line "+start+": unclosed quoted field");
        if(cell.length()>0 || closed || !cells.isEmpty()){cells.add(cell.toString());out.add(new Record(start,cells));}
        if(out.size()>MAX_ROWS+1)throw new IllegalArgumentException("CSV exceeds 5000 channel rows");
        if(out.isEmpty())throw new IllegalArgumentException("CSV is empty");return out;
    }
    public static Parsed parse(String text){
        List<Record> records=records(text);String[] expected=HEADER.split(",");Map<String,Integer> columns=new HashMap<>();
        for(int i=0;i<records.get(0).cells.size();i++){String name=records.get(0).cells.get(i).trim().toLowerCase(Locale.ROOT);if(columns.put(name,i)!=null)throw new IllegalArgumentException("Duplicate column: "+name);}
        if(columns.size()!=expected.length || !columns.keySet().equals(new HashSet<>(Arrays.asList(expected))))throw new IllegalArgumentException("Use the SignalDeck CSV v1 header from Export CSV → Blank template (all 25 columns required)");
        Parsed result=new Parsed();Set<String> names=new HashSet<>();
        for(int i=1;i<records.size();i++){
            Record record=records.get(i);boolean blank=true;for(String s:record.cells)if(!s.trim().isEmpty())blank=false;if(blank)continue;
            try{
                if(record.cells.size()!=expected.length)throw new IllegalArgumentException("Expected 25 columns, found "+record.cells.size());
                Map<String,String> row=new HashMap<>();for(String key:expected)row.put(key,record.cells.get(columns.get(key)));
                if(!v(row,"format_version").equals("1"))throw new IllegalArgumentException("format_version must be 1");
                Row r=new Row();r.line=record.line;ChannelStore.Channel c=new ChannelStore.Channel();r.channel=c;
                c.name=v(row,"name");if(c.name.isEmpty() || c.name.length()>32)throw new IllegalArgumentException("name must contain 1–32 characters");
                if(!names.add(c.name.toLowerCase(Locale.ROOT)))throw new IllegalArgumentException("Repeated channel name in CSV: "+c.name);
                c.rxFreq=frequency(v(row,"rx_mhz"));c.txFreq=frequency(v(row,"tx_mhz"));
                c.type=choice(row,"mode","FM","DMR")==0?1:0;c.band=choice(row,"bandwidth_khz","12.5","25");
                int[] rx=tone(v(row,"rx_tone")),tx=tone(v(row,"tx_tone"));c.rxType=rx[0];c.rxSubCode=rx[1];c.txType=tx[0];c.txSubCode=tx[1];
                c.sq=integer(row,"squelch",0,9,3);c.power=choice(row,"power","Low","High");c.rxOnly=bool(row,"receive_only");
                r.zones=groupNames(v(row,"zones"));r.scanLists=groupNames(v(row,"scan_lists"));
                c.cc=integer(row,"colour_code",0,15,1);c.inSlot=integer(row,"rx_slot",1,2,1)-1;c.outSlot=integer(row,"tx_slot",1,2,1)-1;
                c.txContact=integer(row,"talkgroup",0,16777215,0);c.contactType=choice(row,"contact_type","Private","Group","All");
                if(c.type==0 && c.txContact==0)throw new IllegalArgumentException("DMR talkgroup/call ID must be 1–16777215");
                List<String> groups=splitNames(v(row,"rx_groups"));if(groups.size()>32)throw new IllegalArgumentException("rx_groups allows at most 32 IDs");
                c.groups=new int[groups.size()];for(int g=0;g<groups.size();g++){try{c.groups[g]=Integer.parseInt(groups.get(g));}catch(NumberFormatException e){throw new IllegalArgumentException("rx_groups must contain numeric IDs separated by |");}if(c.groups[g]<1 || c.groups[g]>16777215)throw new IllegalArgumentException("rx_groups IDs must be 1–16777215");}
                c.mode=choice(row,"dmr_mode","Direct","Double_slot")==0?0:4;c.relay=choice(row,"relay","Disconnect","Normal")+1;
                c.interrupt=bool(row,"interrupt")?1:2;c.softSq=bool(row,"soft_squelch");c.softSqLevel=integer(row,"soft_squelch_level",1,10,5);c.notes=row.get("notes");
                result.rows.add(r);
            }catch(IllegalArgumentException e){if(result.errors.size()<50)result.errors.add("Line "+record.line+": "+e.getMessage());}
        }
        if(result.rows.isEmpty() && result.errors.isEmpty())result.errors.add("No channel rows found (the blank template contains only a header)");return result;
    }
    private static String v(Map<String,String> r,String k){return r.get(k).trim();}
    private static int integer(Map<String,String> r,String key,int min,int max,int fallback){String s=v(r,key);if(s.isEmpty())return fallback;try{int n=Integer.parseInt(s);if(n>=min && n<=max)return n;}catch(NumberFormatException ignored){}throw new IllegalArgumentException(key+" must be "+min+"–"+max);}
    private static int choice(Map<String,String> r,String key,String... options){String s=v(r,key);for(int i=0;i<options.length;i++)if(s.equalsIgnoreCase(options[i]))return i;throw new IllegalArgumentException(key+" must be "+String.join(" / ",options));}
    private static boolean bool(Map<String,String> r,String key){String s=v(r,key);if(s.equalsIgnoreCase("true") || s.equals("1"))return true;if(s.equalsIgnoreCase("false") || s.equals("0"))return false;throw new IllegalArgumentException(key+" must be true or false");}
    public static int frequency(String s){try{int hz=new BigDecimal(s).movePointRight(6).intValueExact();if(ChannelStore.inBand(hz))return hz;}catch(ArithmeticException|NumberFormatException ignored){}throw new IllegalArgumentException("Frequency must be MHz in 136–174 or 400–480, with at most 1 Hz precision");}
    private static int[] tone(String s){
        if(s.isEmpty() || s.equalsIgnoreCase("None"))return new int[]{0,0};String upper=s.toUpperCase(Locale.ROOT);
        if(upper.startsWith("CTCSS ")){try{double hz=Double.parseDouble(s.substring(6).trim());for(int i=0;i<ToneTable.CTCSS.length;i++)if(Math.abs(ToneTable.CTCSS[i]-hz)<0.00001)return new int[]{1,i};}catch(NumberFormatException ignored){}}
        if(upper.matches("D[0-7]{1,3}[NI]")){int index=ToneTable.dcsIndex(upper);if(index>=0)return new int[]{upper.endsWith("I")?3:2,index};}
        throw new IllegalArgumentException("Invalid tone '"+s+"': use None, CTCSS 88.5, D023N or D023I");
    }
    public static List<String> splitNames(String text){
        List<String> out=new ArrayList<>();if(text.isEmpty())return out;StringBuilder part=new StringBuilder();boolean escaped=false;
        for(char ch:text.toCharArray()){if(escaped){if(ch!='|' && ch!='\\')throw new IllegalArgumentException("Only \\| and \\\\ escapes are supported");part.append(ch);escaped=false;}else if(ch=='\\')escaped=true;else if(ch=='|'){out.add(part.toString());part.setLength(0);}else part.append(ch);}
        if(escaped)throw new IllegalArgumentException("Trailing backslash in group list");out.add(part.toString());return out;
    }
    private static List<String> groupNames(String s){List<String> out=new ArrayList<>();for(String name:splitNames(s)){name=name.trim();if(name.isEmpty() || name.length()>32)throw new IllegalArgumentException("Zone/list names must contain 1–32 characters");for(String old:out)if(old.equalsIgnoreCase(name))throw new IllegalArgumentException("Repeated zone/list name: "+name);out.add(name);}return out;}
    public static String joinNames(List<String> names){List<String> escaped=new ArrayList<>();for(String s:names)escaped.add(s.replace("\\","\\\\").replace("|","\\|"));return String.join("|",escaped);}
    private static String toneText(int type,int index){if(type==0)return "None";if(type==1 && ToneTable.ctcssHz(index)>0)return "CTCSS "+String.format(Locale.US,"%.1f",ToneTable.ctcssHz(index));if((type==2 || type==3) && ToneTable.dcsCode(index)!=null)return "D"+ToneTable.dcsCode(index)+(type==3?"I":"N");throw new IllegalArgumentException("Invalid tone index in channel book");}
    public static String write(List<Row> rows){
        StringBuilder out=new StringBuilder("\uFEFF").append(HEADER).append("\r\n");
        for(Row row:rows){ChannelStore.Channel c=row.channel;if(c.type!=0 && c.type!=1)throw new IllegalArgumentException("CSV v1 supports FM/DMR only: "+c.name);if(c.encryptSw!=2)throw new IllegalArgumentException("CSV v1 does not export encrypted channels: "+c.name);
            if(c.band<0 || c.band>1 || c.power<0 || c.power>1 || c.contactType<0 || c.contactType>2 || (c.mode!=0 && c.mode!=4) || c.relay<1 || c.relay>2 || c.interrupt<1 || c.interrupt>2)throw new IllegalArgumentException("Unsupported saved settings in channel: "+c.name);
            StringJoiner groups=new StringJoiner("|");for(int id:c.groups)groups.add(Integer.toString(id));
            String[] cells={"1",c.name,BigDecimal.valueOf(c.rxFreq,6).toPlainString(),BigDecimal.valueOf(c.txFreq,6).toPlainString(),c.type==1?"FM":"DMR",c.band==0?"12.5":"25",toneText(c.rxType,c.rxSubCode),toneText(c.txType,c.txSubCode),""+c.sq,c.power==0?"Low":"High",""+c.rxOnly,joinNames(row.zones),joinNames(row.scanLists),""+c.cc,""+(c.inSlot+1),""+(c.outSlot+1),""+c.txContact,new String[]{"Private","Group","All"}[c.contactType],groups.toString(),c.mode==0?"Direct":"Double_slot",c.relay==1?"Disconnect":"Normal",""+(c.interrupt==1),""+c.softSq,""+c.softSqLevel,c.notes==null?"":c.notes};
            // Check the exact exported representation with the same validator used for imports.
            append(out,cells);
        }
        if(out.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8).length>MAX_CHARS)throw new IllegalArgumentException("Export exceeds 2 MB; choose a smaller zone or scan list");
        if(!rows.isEmpty()){Parsed check=parse(out.toString());if(!check.valid())throw new IllegalArgumentException(String.join("\n",check.errors));}return out.toString();
    }
    private static void append(StringBuilder out,String[] cells){for(int i=0;i<cells.length;i++){if(i>0)out.append(',');out.append('"').append(cells[i].replace("\"","\"\"")).append('"');}out.append("\r\n");}
    public static String template(boolean examples){
        if(!examples)return write(Collections.emptyList());
        Row fm=new Row();fm.channel=new ChannelStore.Channel();fm.channel.name="Example FM, receive only";fm.channel.type=1;fm.channel.rxFreq=fm.channel.txFreq=446106250;fm.channel.band=0;fm.channel.rxOnly=true;fm.zones=Arrays.asList("Local");fm.scanLists=Arrays.asList("My scan");
        Row dmr=new Row();dmr.channel=new ChannelStore.Channel();dmr.channel.name="Example DMR";dmr.channel.rxFreq=dmr.channel.txFreq=433450000;dmr.channel.band=0;dmr.channel.rxOnly=true;dmr.channel.txContact=9;dmr.channel.groups=new int[]{9};dmr.zones=Arrays.asList("Digital");dmr.scanLists=Arrays.asList("My scan");
        return write(Arrays.asList(fm,dmr));
    }
}
