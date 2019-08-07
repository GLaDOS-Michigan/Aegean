CREATE ALIAS IF NOT EXISTS READ_CLOB FOR "org.h2.tools.Recover.readClob";
CREATE ALIAS IF NOT EXISTS READ_BLOB FOR "org.h2.tools.Recover.readBlob";
-- pageSize: 2048 writeVersion: 3 readVersion: 3
-- head 1: writeCounter: 1047 log key: 0 trunk: 0/0 crc expected -224803999 got -224803999 (ok)
-- head 2: writeCounter: 1047 log key: 0 trunk: 0/0 crc expected -224803999 got -224803999 (ok)
-- firstTrunkPage: 0 firstDataPage: 0
-- page 3: free list 
-- 3 111111
-- page 4: data leaf (last) parent: 0 table: -1 entries: 5 columns: 6
CREATE TABLE O_M1(C0 VARCHAR, C1 VARCHAR, C2 VARCHAR, C3 VARCHAR, C4 VARCHAR, C5 VARCHAR);
INSERT INTO O_M1 VALUES(0, 0, 0, 8, 'OFF,0,,', '0,1,2,3');
INSERT INTO O_M1 VALUES(15, 0, 15, 7, 'OFF,0,,', '0,1,2,3');
INSERT INTO O_M1 VALUES(16, 1, 15, 0, 'OFF,0,,d', '0');
INSERT INTO O_M1 VALUES(19, 0, 19, 6, 'OFF,0,,', '0,1');
INSERT INTO O_M1 VALUES(20, 1, 19, 0, 'OFF,0,,d', '0');
-- page 5: data overflow (last)
-- page 6: data leaf (last) parent: 0 table: 19 entries: 0 columns: 2
-- page 7: data leaf  parent: 0 table: 15 entries: 1 columns: 4
--   next: 5
-- chain: 5 type: 19 size: 1983
CREATE TABLE O_15(C0 VARCHAR, C1 VARCHAR, C2 VARCHAR, C3 VARCHAR);
INSERT INTO O_15 VALUES(1, 'Hello', X'29599ec58507502d44fcf8bf6a4338d8720e64be3be64024001894e3837d9a527a73e0213ec784140fc8245161180cd57c87a09f7f3ba963223a093b2fc3d961f0f04c4717181159151a6194d82da839829ea1d1eef7ab7aecb85b08ce86d323b723e70d290c0302fc7751285cf69ff08f32dba42111b20410bb11005a290da998c55552506f62adfb7283d991a0dbdf0e4cf9aa9aaaa568e973d6c54e650a4cbda6d75198948698c63d36811682c844ed5ee266a58c56e6f9861748ae0889282ce3c8d28f27eeaecc7649f38801dc9ab475eb4d93ba14cab231a4db871750d9e7d7f00c860db891132a667d7b95339e4913926faf1ba7a70cccfb54f2aba2da45d28283aad25756d20dbe9e4c67ac9e2ef72a632911c99596a78da69b7dd0593c049ab9880ff6e2213b6e4029d7f98ad3f43c4801f7aac70e717c362ffb4a859ad34c4fb1e690309dcf3c5d18f0251d5d6ff40c6fcba6ec8bcc97b246f44450954db5edba5f936c4a97edc315a8c813a90ea80555348613ce0fb5c189610acbad5a7a48b6a718620304f215f206f2a6accb781f57aba5fab007e5842734de94e6e606f7a846abf2950e104062936b97327a6575527072fa65a6b87f29c392efedd3bebda9c442e56727900a4d7d5ed008f12425599d8a4892a0935892452359fe71c0d4de6a1b2388a8a95130b6e3525ad23b3070487f580acd3da9134a26428954b73c59409869d5798b9ef742deadfff08bee44f959ec76e88c82ccb8bcddf059b369648cb1d3da403f5cbd35f86b5f883b41381d35a1b624c998add698a08ebc72873b234267bb728165320185c428abfa50133925890274fce85c9627a4b928073dcbae9a22a17e450a29235bf0a8ff0ac4ebe4f37c49a93596cb3176108b2117baa56f602dd83c1c148a7dd9fa454ba9d082166006a415fa6b87786dcd8cf244f2076c0aae07b747fb85d93ca1b0812440cab4a38e8e3fffb38499df3f228305d510455a385d77e91a39183fac8c69cc91b3b0da560fcd084dcfcca86f6410fb2596535d2a13814fca99f574f9c741c5d7ec18a36c581437eeb918676b2f74d6dddf33d0e186407ee65f327ab8d0003dfbf13e125de57deb30d27bf029188d1cc516a19ab875a030415d084dc29cfb07a6788b94595e00f052d3785270dee4b1202ceed778cc054d03a28d189a1f577c39f7e4ec9e5942d3752dfdca43219dc4b58db5cacc14763d66061415149a18373be73810cfb31d81921b41c753390a6c17e2b59131400f660bdfe777bef2f08a5c307893f521c52b6a61cd20f1a54d967ddefbbdea8a901d10bcee903773f2e57566b60cdd178690195e48181c892f1ca59bdb62ef9a61c1e693abb307055df53f5229c61ac5fb9f6388e293d559c68a45dfc62f4431df3a5d6c52eca664fd6f15865920137597d4386b9c84741f92b21fcec7aeb40f30f29850109a67a27e65a32ba218535226da15bf5d6f62f33c10dfb938587251c2968738e92bd8e9dae9a6c499feaafa74be144793175b5fd0035ea5cf47b66a3df71b8121c2d6ee4fa1789f0fa52075d7d65ae486567e1082fcb9063dee3e6fa835b218d67f9d2992c39d1225f1912e7e952af851637d5b1fee0c0935e805111d8c526498d2cb319ec572c99b7f539274d5e2218d9d5b033795a622b5f7fc269a90900c03de17f8660c811fbd616c152e882f157309bff69d7d8150ecbedfc1b29c0c2ff19e58b35478eb1ac1c83f81c5400c86fb7dd955a2d1774ea8319db8512306507cd8464415bf565213bfb7ae531de065fc9232184752df1b337ae06daddb71d426069919be097a7762f402b637fe9c958ef96f38c5d933dfef7978f382c01a2998a108a14b5690f28442829fb490c327fe9cef2e60f91589a16c5ad384201cd200b73a69a67aae807bcb0727099e959c82b8929f617b4f310a84049ff887416ea90be4b7f33daafcf198ce0ae81ce4e9366be568fcc8d6f9e8d0a09cf59138f3a4d0e7f87cb963b494be45325aa883995df82d6204b994ddd39f5c209dd7f44dc1b11bddf344602fec53dbc00f74903e941453c77a4f4c7c885571b8640122073aa534f60ca5d81afa47ea2bf4732be581d53e506931957edc5560ebd55ea13b958d32ce9126cf9cef0f1deb2e6671812a287022a07238cd2b2d1511692b8aab80ab008955b854ee8f26907b50a67f29f77ab0ac639a29fe4245505eb4cce1172db7abe293538d98567a9d2d5cc15bfb25c4b4d933109adc4e96c1de6ed1b3d26092488271784092e947e7ff8bbfc091bd701f928fb8de95c6f525665851cf560b19c3999a6234fc0ac6b8cc4ff9a55b0546d865dfa0c2b98679cb390a9c361b0d5076f4441246049619f0eb0e86f466c573c3f0ab5a92239fdede6f9bc1ff16bea80cb7f4456ad968bd8c105eba5388b7e52ccddc4cbc2992b71fc82a391f57eacda8bdd172c8ff8ef364af180646e9edab44e08cf575019e2b5125085d2be60ef49155b384a2a4cbce99340acb18bab82c9136b4610e36ff8bd394c36e1b5dd152f317c8d1a9fbad5c5e754fadecc318c4b9ad28ee5933b22d8f6354d1753d16ac1194dca13b888ee4963b8ac8f8875d9c13d03d4a6782182381a50d2bf6a08087fe60f4593f63e8c4b3318087ca6d24b0ef54e607c14f33d80cf48d8b4cc7a98d0ab3062efeffbf082635edc095cbd17f9195b92d32f0b3cc6ffbab1dc70d93f2d921bb455e3e6c90857842479830cc8069da9a7c21790d20dbeb0a0287dbbdbb50669b7845ae07f35b45e746f7213d4ee820d0550bd7ae6b622fec3a1f79dcbdbb', '                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                ');
-- page 8: data leaf (last) parent: 0 table: 0 entries: 13 columns: 4
CREATE TABLE O_0(C0 VARCHAR, C1 VARCHAR, C2 VARCHAR, C3 VARCHAR);
INSERT INTO O_0 VALUES(1, 0, 6, 'SET DEFAULT_LOCK_TIMEOUT 2000');
INSERT INTO O_0 VALUES(3, 0, 6, 'SET DEFAULT_TABLE_TYPE 0');
INSERT INTO O_0 VALUES(5, 0, 6, 'SET CACHE_SIZE 16384');
INSERT INTO O_0 VALUES(7, 0, 6, 'SET CLUSTER ''''');
INSERT INTO O_0 VALUES(9, 0, 6, 'SET WRITE_DELAY 500');
INSERT INTO O_0 VALUES(11, 0, 6, 'SET CREATE_BUILD 128');
INSERT INTO O_0 VALUES(13, 0, 2, 'CREATE USER SA SALT ''cc3d93b1c5d47f0e'' HASH ''3130251176d54a544a6b308e6863c7ed96bd3cc602ea413f13dc67f23c8ca9a0'' ADMIN');
INSERT INTO O_0 VALUES(16, 0, 1, 'CREATE PRIMARY KEY PUBLIC.PRIMARY_KEY_2 ON PUBLIC.TEST(ID)');
INSERT INTO O_0 VALUES(17, 0, 5, 'ALTER TABLE PUBLIC.TEST ADD CONSTRAINT PUBLIC.CONSTRAINT_2 PRIMARY KEY(ID) INDEX PUBLIC.PRIMARY_KEY_2');
INSERT INTO O_0 VALUES(19, 0, 0, STRINGDECODE('CREATE CACHED TABLE PUBLIC.\"test 2\"(\n    ID INT NOT NULL,\n    NAME VARCHAR\n)'));
INSERT INTO O_0 VALUES(20, 0, 1, 'CREATE PRIMARY KEY PUBLIC.PRIMARY_KEY_C ON PUBLIC."test 2"(ID)');
INSERT INTO O_0 VALUES(21, 0, 5, 'ALTER TABLE PUBLIC."test 2" ADD CONSTRAINT PUBLIC.CONSTRAINT_C PRIMARY KEY(ID) INDEX PUBLIC.PRIMARY_KEY_C');
INSERT INTO O_0 VALUES(15, 0, 0, STRINGDECODE('CREATE CACHED TABLE PUBLIC.TEST COMMENT '';-)''(\n    ID INT NOT NULL,\n    NAME VARCHAR,\n    B BLOB,\n    C CLOB\n)'));
---- Schema ----------
CREATE USER SA SALT 'cc3d93b1c5d47f0e' HASH '3130251176d54a544a6b308e6863c7ed96bd3cc602ea413f13dc67f23c8ca9a0' ADMIN;
CREATE CACHED TABLE PUBLIC.TEST COMMENT ';-)'(
    ID INT NOT NULL,
    NAME VARCHAR,
    B BLOB,
    C CLOB
);
CREATE CACHED TABLE PUBLIC."test 2"(
    ID INT NOT NULL,
    NAME VARCHAR
);
CREATE PRIMARY KEY PUBLIC.PRIMARY_KEY_2 ON PUBLIC.TEST(ID);
CREATE PRIMARY KEY PUBLIC.PRIMARY_KEY_C ON PUBLIC."test 2"(ID);
INSERT INTO PUBLIC.TEST SELECT * FROM O_15;
DROP TABLE O_0;
DROP TABLE O_15;
DROP TABLE O_M1;
DROP ALIAS READ_CLOB;
DROP ALIAS READ_BLOB;
SET DEFAULT_LOCK_TIMEOUT 2000;
SET DEFAULT_TABLE_TYPE 0;
SET CACHE_SIZE 16384;
SET CLUSTER '';
SET WRITE_DELAY 500;
SET CREATE_BUILD 128;
ALTER TABLE PUBLIC.TEST ADD CONSTRAINT PUBLIC.CONSTRAINT_2 PRIMARY KEY(ID) INDEX PUBLIC.PRIMARY_KEY_2;
ALTER TABLE PUBLIC."test 2" ADD CONSTRAINT PUBLIC.CONSTRAINT_C PRIMARY KEY(ID) INDEX PUBLIC.PRIMARY_KEY_C;
---- Transaction log ----------
---- Statistics ----------
-- page count: 9 empty: 0 free: 0
-- page data head: 109 empty: 5144 rows: 2939
-- page count type: 1 44% count: 4
-- page count type: 3 11% count: 1
-- page count type: 6 11% count: 1
