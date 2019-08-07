#include "BFT_fork_Forker.h"
#include <unistd.h>
#include <stdlib.h>
#include <sys/wait.h>
#include <iostream>

JNIEXPORT jint JNICALL Java_BFT_fork_Forker_sysfork
  (JNIEnv *, jobject)
{
  return fork();
}

JNIEXPORT void JNICALL Java_BFT_fork_Forker_sysexit
  (JNIEnv *, jobject)
{
  exit(0);
}

JNIEXPORT void JNICALL Java_BFT_fork_Forker_syswait
  (JNIEnv *, jobject)
{
	int ret;
	std::cout << "\t\tABOUT TO CALL EXIT!!!" << std::endl;
	wait(&ret);
	if(WIFEXITED(ret)) {
		std::cout << "CHILD EXITED NORMALLY" << std::endl;
	}
	else {
		std::cout << "CHILD DIDN'T EXIT NORMALLY" << std::endl;
	}
}
