default:
	g++ -I/usr/java/default/include/ -I/usr/java/default/include/linux/ -g -fPIC -c  Forker.cc -o Forker.o
	g++ -shared -Wl,-soname,libForkerCheat.so.1 \
-lc Forker.o -o libForkerCheat.so
