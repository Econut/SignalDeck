import csv
import hashlib
import sqlite3
import tempfile
import unittest
from contextlib import closing
from pathlib import Path
from p8_channels import FIELDS, convert

class P8ChannelImportTest(unittest.TestCase):
    def test_receive_only_export_preserves_source_and_handles_stock_enum_direction(self):
        with tempfile.TemporaryDirectory() as tmp:
            db, out = Path(tmp)/'stock.db', Path(tmp)/'channels.csv'
            with closing(sqlite3.connect(db)) as c:
                c.execute('CREATE TABLE Channel (' + ','.join(FIELDS) + ')')
                c.executemany('INSERT INTO Channel VALUES (' + ','.join('?' for _ in FIELDS) + ')', [
                    (7,'Narrow FM',0,145500000,145500000,0,2,0,0,0,0,2),
                    (9,'=Unsafe CSV name',0,433500000,433500000,1,1,1,5,1,5,1),
                    (10,'Digital omitted',1,0,0,0,0,0,0,0,0,0)])
                c.commit()
            before = db.read_bytes(); report = convert(db,out)
            self.assertEqual(before,db.read_bytes()); self.assertEqual(2,report['channels'])
            with out.open(newline='',encoding='utf-8') as f: rows=list(csv.DictReader(f))
            self.assertEqual(['NFM','FM'],[r['Mode'] for r in rows])
            self.assertTrue(all(r['Duplex']=='off' and r['Tone']=='' for r in rows))
            self.assertEqual("'=Unsafe CSV name",rows[1]['Name'])
            self.assertEqual(hashlib.sha256(before).hexdigest(),report['source_sha256'])
            with self.assertRaises(FileExistsError): convert(db,out)
            Path(str(db)+'-wal').write_bytes(b'not a consistent snapshot')
            with self.assertRaises(ValueError): convert(db,Path(tmp)/'other.csv')

if __name__ == '__main__': unittest.main()
