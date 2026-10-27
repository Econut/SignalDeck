"""Convert a consistent, HOST-side stock Channel database copy to a receive-only CHIRP CSV.

No ADB, root, serial access or source writes. Does not export contacts, IDs or encryption keys.
Tone filters are deliberately not translated until the P8's tone-index tables are verified.
Original wire fields are retained as a comment so that no guessed enum is written to hardware.
"""
import argparse
import csv
import hashlib
import sqlite3
from contextlib import closing
from pathlib import Path

FIELDS = ('channelID', 'channelName', 'channelFormat', 'recvFreqAnalog', 'sendFreqAnalog',
          'broadbandSettings', 'silenceLevel', 'rxcxcss_type', 'rxcxcss', 'txcxcss_type', 'txcxcss', 'monitor')
HEADER = ['Location', 'Name', 'Frequency', 'Duplex', 'Offset', 'Tone', 'rToneFreq', 'cToneFreq',
          'DtcsCode', 'DtcsPolarity', 'RxDtcsCode', 'CrossMode', 'Mode', 'TStep', 'Skip', 'Power', 'Comment']

def rows_from_database(source):
    source = Path(source).resolve(strict=True)
    if not source.is_file():
        raise ValueError('use a regular saved database file')
    if Path(str(source) + '-wal').exists():
        raise ValueError('WAL sidecar present: obtain a consistent host database snapshot first')
    # immutable avoids SHM/journal creation; never use on a live/changing database.
    with closing(sqlite3.connect(source.as_uri() + '?mode=ro&immutable=1', uri=True)) as db:
        db.execute('PRAGMA query_only=ON')
        columns = {row[1] for row in db.execute('PRAGMA table_info("Channel")')}
        if not set(FIELDS) <= columns:
            raise ValueError('stock P8 Channel schema not recognised')
        query = 'SELECT ' + ','.join('"' + f + '"' for f in FIELDS) + ' FROM "Channel" ORDER BY "channelID"'
        rows = []
        for values in db.execute(query):
            r = dict(zip(FIELDS, values))
            if r['channelFormat'] != 0:  # P8 format0=analogue; SignalDeck uses type1=FM
                continue
            hz = r['recvFreqAnalog']
            if not isinstance(hz, int) or not (136000000 <= hz <= 174000000 or 400000000 <= hz <= 480000000):
                raise ValueError('analogue RX frequency is outside the current SignalDeck import bands')
            if r['broadbandSettings'] not in (0, 1):
                raise ValueError('unrecognised P8 bandwidth enum')
            if any(not isinstance(r[f], int) or not 0 <= r[f] <= 255 for f in
                   ('silenceLevel', 'rxcxcss_type', 'rxcxcss', 'txcxcss_type', 'txcxcss', 'monitor')):
                raise ValueError('invalid wire enum in stock channel')
            rows.append(r)
        return rows

def convert(source, destination):
    source, destination = Path(source), Path(destination)
    before = hashlib.sha256(source.read_bytes()).hexdigest()
    rows = rows_from_database(source)
    after = hashlib.sha256(source.read_bytes()).hexdigest()
    if before != after:
        raise ValueError('source changed while being read')
    with destination.open('x', newline='', encoding='utf-8') as out:
        writer = csv.DictWriter(out, fieldnames=HEADER); writer.writeheader()
        for number, r in enumerate(rows, 1):
            name = str(r['channelName'] or ('P8 ' + str(r['channelID'])))
            if name[:1] in '=+-@': name = "'" + name  # spreadsheet formula protection
            raw = '; '.join(f'{f}={r[f]}' for f in FIELDS if f not in ('channelName', 'channelFormat'))
            writer.writerow(dict(Location=number, Name=name, Frequency=f"{r['recvFreqAnalog']/1e6:.6f}",
                                 Duplex='off', Offset='0.000000', Tone='', Mode='NFM' if r['broadbandSettings']==0 else 'FM',
                                 Comment='P8 receive-only import; carrier receive (tone filters not converted); ' + raw))
    return dict(channels=len(rows), source_sha256=before, receive_only=True, tone_filters_converted=False)

if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('saved_database', type=Path); parser.add_argument('new_csv', type=Path)
    args = parser.parse_args(); print(convert(args.saved_database, args.new_csv))
