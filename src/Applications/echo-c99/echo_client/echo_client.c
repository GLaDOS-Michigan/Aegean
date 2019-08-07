// Copyright (c) 2010 Gianluca Ciccarelli
// Module: echo_client
// $Id: echo_client.c 532 2010-08-27 20:59:17Z glc $

#include <stdio.h>
#include <stdlib.h>
#include <stdbool.h>
#include <assert.h>
#include <time.h>
#include <openssl/evp.h>
#include <openssl/sha.h>

#include "../../../BFT-c99/clientshim/client_shim_node.h"
#include "../../../BFT-c99/clientshim/client_shim_glue.h"
#include "../../../BFT-c99/clientshim/client_shim_interface.h"
#include "../../../BFT-c99/util/rngs.h"
#include "../../../BFT-c99/util/integer_byte_conv.h"
#include "../../../BFT-c99/messages/mac_message.h"
#include "../../../BFT-c99/messages/reply.h"
#include "../../../BFT-c99/messages/msg_types.h"
#include "../../../BFT-c99/util/log.h"
#include "../../../BFT-c99/util/keygen.h"
#include "../../../BFT-c99/configuration/configuration.h"

#define KEYS_DIR "/Users/gc/Code/bft/build/keys/"

extern Parameters parms;
static StructInfo *	echo_canonical_entry	(StructInfo **opts);
static void		echo_return_reply	(StructInfo *reply);

int 
main(int   argc, 
     char *argv[])
{	
	if (argc < 4) {
		printf("Error: too few arguments.\n"); 
		printf("Usage: %s <id> <config_file> <op_count> "
				"|<read_ratio>| |<request_size>|\n", 
				argv[0]); 
		return EXIT_FAILURE;
	}
	
	// Init parms
	FILE *cfg_handle;
  	if ((cfg_handle = fopen(argv[2], "r")) == NULL)
		die("Cannot open configuration file");
	assert(!prm_is_initialized(&parms));
	if(cfg_read_config(cfg_handle, &parms) != 0)
		die("Problems reading configuration file");
	fclose(cfg_handle);
	assert(prm_is_initialized(&parms));

	OpenSSL_add_all_algorithms();
	double readratio = 0.0;
	int reqsize = 0;
	if (argc >= 5)
		readratio = strtod(argv[4], NULL);
	if (argc == 6)
		reqsize = (int)strtol(argv[5], (char**)NULL, 10);
	printf("readratio = %f, reqsize = %d\n", readratio, reqsize); 

	ClientShimGlue *glue = calloc(1, sizeof(ClientShimGlue));
	assert(glue);
	glue->csg_canonical_entry = echo_canonical_entry;
	glue->csg_return_reply = echo_return_reply;

	ClientShimNode  *csn  = csn_new(argv[2], 
					KEYS_DIR, 
		   			0, 
		   			glue);
	assert(csn);

	int clientid = (int)strtol(argv[1], (char**)NULL, 10);
	int opcount  = (int)strtol(argv[3], (char**)NULL, 10);

	printf("@%s> Client ID: %d\n", __func__, clientid); 
	printf("@%s> Operations to execute: %d\n", __func__, opcount);

	time_t starttime = 0;
	time(&starttime);
	printf("Start: %s\n", ctime(&starttime));

	int readcount = 0;
	PlantSeeds(1234567890);

	size_t      oplen  = reqsize + 15 * sizeof(uint8_t);
	StructInfo *op     = util_log_structinfo_new(oplen);
	StructInfo *repcom = NULL;

	for (int i = 1; i <= opcount; i++) {
		time_t opstart = 0;
		time(&opstart);

		// The server is supposed to reply the same content we put in
		// the request.
		uint8_t *i_as_byte = calloc(4, sizeof(uint8_t));
		formatted_pack(i_as_byte, "w", &i);
		formatted_pack(op->buf, "w", &i);

		if (Random() < readratio) {
			cs_api_execute_read_only_request(csn, op);
			readcount++;

			time_t opend = 0;
			time(&opend);
			printf("Request %d:\n"
			       "\tStart time: %s\tEnd time: %s"
			       "\tClient: %d\n", 
			       i, 
			       ctime(&opstart),
			       ctime(&opend), 
			       clientid);

		} else {
			repcom = cs_api_execute_request(csn, op);
			assert(repcom);

			time_t opend = 0;
			time(&opend);
			printf("Request %d:\n"
			       "\tStart time: %s\tEnd time: %s"
			       "\tClient: %d\n", 
			       i, 
			       ctime(&opstart),
			       ctime(&opend), 
			       clientid);

			//printf("@%s> Reply from the server:\n", __func__); 
			//util_keygen_print_hex(repcom->buf, repcom->len);
		
			
			bool res = true;
			MacMessage *mm = msg_mm_unpack(repcom, msg_rep_unpack);
			ReplyType  *rt = mm->payload;
			printf("@%s> Response\n", __func__); 
			msg_reply_print(rt);
			for (int k = 0; k < 4; k++) 
				if (rt->reply[k] != i_as_byte[k])
					res = false;
			if (!res) {
				// The server put garbage in the content
				printf("Broken :(\n"); 

				// Problem: the server assumes we are using
				// the same kind of content used in the Java
				// version.
				//exit(EXIT_FAILURE);
			}
			msg_mm_free(mm, msg_reply_free);
			mm = NULL;
			
		}
		CLEAN(i_as_byte);
	}
	time_t endtime = 0;
	time(&endtime);
	printf("End: %s", ctime(&endtime));
	printf("Reads: %d\tTotal: %d\n", readcount, opcount);

	// Clean up
	util_log_structinfo_free(op);
	csn_free(csn);
	return EXIT_SUCCESS;
}

static StructInfo *
echo_canonical_entry(StructInfo         **opts)
{
	return opts[0];
}

static void
echo_return_reply(StructInfo *reply)
{
}
