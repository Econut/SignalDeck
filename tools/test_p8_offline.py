import os
import subprocess
import tempfile
import unittest
import wave
from pathlib import Path

class P8OfflineToolTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.repo = Path(__file__).resolve().parents[1]
        cls.classes = cls.repo/'build'/'p8-offline'
        cls.classes.mkdir(parents=True,exist_ok=True)
        cls.java = str(Path(os.environ['JAVA_HOME'])/'bin'/'java.exe')
        javac = str(Path(os.environ['JAVA_HOME'])/'bin'/'javac.exe')
        src = cls.repo/'app/src/main/java/com/macdmr/radio'
        subprocess.run([javac,'-d',str(cls.classes),*[str(src/n) for n in ('Frame.java','P8Protocol.java','PcmFramer.java')],str(cls.repo/'tools/P8Offline.java')],check=True,capture_output=True)

    def run_tool(self,*args):
        return subprocess.run([self.java,'-cp',str(self.classes),'P8Offline',*map(str,args)],capture_output=True,text=True)

    def test_literal_reply_and_invalid_checksum(self):
        self.assertIn('SMS received',self.run_tool('decode','6836020090B800010510').stdout)
        self.assertNotEqual(0,self.run_tool('decode','6836020090B800010410').returncode)

    def test_saved_pcm_becomes_exact_wav_and_output_cannot_overwrite(self):
        with tempfile.TemporaryDirectory() as tmp:
            source,out=Path(tmp)/'saved.bin',Path(tmp)/'new.wav'
            samples=b'\x00\x20\x00\xe0'*40
            raw=b'noise'+b'\xbb\x00\x00\xa0\x00\x00'+samples+b'\x44'
            source.write_bytes(raw)
            result=self.run_tool('pcm-wav',source,out)
            self.assertEqual(0,result.returncode,result.stderr)
            with wave.open(str(out)) as w:
                self.assertEqual((1,2,8000,80),(w.getnchannels(),w.getsampwidth(),w.getframerate(),w.getnframes()))
                self.assertEqual(samples,w.readframes(80))
            self.assertEqual(raw,source.read_bytes())
            before=out.read_bytes();self.assertNotEqual(0,self.run_tool('pcm-wav',source,out).returncode)
            self.assertEqual(before,out.read_bytes())
            loud=Path(tmp)/'loud.wav';result=self.run_tool('pcm-wav',source,loud,'18')
            self.assertEqual(0,result.returncode,result.stderr);self.assertIn('clipped=80',result.stdout)

    def test_invalid_capture_never_creates_wav(self):
        with tempfile.TemporaryDirectory() as tmp:
            source,out=Path(tmp)/'invalid.bin',Path(tmp)/'none.wav';source.write_bytes(b'garbage')
            self.assertNotEqual(0,self.run_tool('pcm-wav',source,out).returncode)
            self.assertFalse(out.exists())

if __name__=='__main__': unittest.main()
