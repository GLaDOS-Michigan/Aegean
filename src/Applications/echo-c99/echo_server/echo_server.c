// Copyright (c) 2010 Gianluca Ciccarelli
// Module:
// $Id: echo_server.c 616 2010-10-18 22:57:27Z porterde $

#include <assert.h>
#include <stdio.h>
#include <stdlib.h>
#include <openssl/evp.h>
#include <unistd.h>
#include <stdbool.h>
#include <string.h>
#include <stdint.h>
#include <errno.h>
#include <event2/event.h>
#include <event2/buffer.h>
#include <event2/bufferevent.h>
#include <inttypes.h>
#include "../../../BFT-c99/servershim/shim_base_node.h"
#include "../../../BFT-c99/servershim/server_shim_interface.h"
#include "../../../BFT-c99/servershim/glue_shim_interface.h"
#include "../../../BFT-c99/util/keygen.h"
#include "../../../BFT-c99/util/bft_types.h"
#include "../../../BFT-c99/util/log.h"
#include "../../../BFT-c99/configuration/configuration.h"
#include "../../../BFT-c99/messages/entry.h"
#include "../../../BFT-c99/messages/msg_types.h"
#include "../../../BFT-c99/parameters.h"
#include "../../../BFT-c99/base_node.h"

#define MAX_LINE 16384
#define NUMSOCKETS 4
#undef  BUFSIZE
#define BUFSIZE 4096

#undef	KEYS_DIR
#define	KEYS_DIR	"../../../../bft_c99_tests/keys/"

extern Parameters parms;

typedef struct echo_server_state {
	seqno_t next_seqno;
	int count;
	int loss_rate;
	bool done_once;
	ShimBaseNode *sbn;
} EchoServerState;

/**
 * State of the application. This is global because the interface of the
 * functions implementing the glue has a bad design, coming from the fact that
 * the Java version assumed the EchoServer is an object and as such has an
 * immediately associated state.
 */
EchoServerState *st;


/*
 * The caller must free the return value.
 */
static char *
ip_to_string(const unsigned long ip)
{
	char *ip_as_string = calloc(INET_ADDRSTRLEN * 3, 1);
	if (!ip_as_string) {
		fprintf(stderr, "@%s> Memory allocation error\n", __func__); 
		exit(EXIT_FAILURE);
	}
	struct in_addr address;
	address.s_addr = htonl(ip);
	inet_ntop(AF_INET, &address, ip_as_string, INET_ADDRSTRLEN * 3);
	return ip_as_string;
}

/*
 * The caller must free the return value.
 */
static char *
port_to_string(const in_port_t port)
{
	char *port_as_string = calloc(10, 1);
	if (!port_as_string) {
		fprintf(stderr, "@%s> Memory allocation error\n", __func__); 
		exit(EXIT_FAILURE);
	}
	snprintf(port_as_string, 10, "%d", port);
	return port_as_string;
}






/* Implementation of the glue_shim_interface API */


/**
 * @param command_batch a sequence of EntryType structs.
 */
static void 
echo_server_exec(const GSequence	*command_batch,  
		 const seqno_t		seq_no, 
		 const NonDeterminism	*nd, 
		 const bool		take_cp)
{
	fprintf(stderr, "@%s> Invoked\n", __func__); 
	st->next_seqno++;
	const bool to_cache = true;

	for (GSequenceIter *it = g_sequence_get_begin_iter((GSequence *)command_batch);
			!g_sequence_iter_is_end(it);
			it = g_sequence_iter_next(it)) {

		EntryType *command = g_sequence_get(it);

		printf("@%s> Command the EchoServer wants to execute:\n", __func__); 
		msg_entry_type_print(command);
		
		
		StructInfo *pkdcom = util_log_structinfo_new(command->payload_size);
		for (int i = 0; i < pkdcom->len; i++) 
			pkdcom->buf[i] = command->payload[i];

		sshm_ssi_result(st->sbn,
				pkdcom,
				command->client_id, 
				command->request_id,
				seq_no,
				to_cache);
		if ((command->request_id % 100) == 0) {
			char *message = calloc(256, 1);
			assert(message);
			snprintf(message, 
				 256, 
				 "watch every 100: %" PRIu32 "", 
				 command->request_id/100);
			StructInfo *pkdmessage = util_log_structinfo_new(strlen(message));
			for (int i = 0; i < pkdmessage->len; i++) 
				pkdmessage->buf[i] = message[i];
			sshm_ssi_result(st->sbn,
					pkdmessage,
					command->client_id,
					command->request_id,
					seq_no,
					!to_cache);

			CLEAN(message);
			util_log_structinfo_free(pkdmessage);
			pkdmessage = NULL;
		}
		util_log_structinfo_free(pkdcom);
		pkdcom = NULL;
	}

	if (take_cp) {
		StructInfo *app_cp_token = util_log_structinfo_new(8);
		assert(app_cp_token);
		sshm_ssi_return_cp(st->sbn,
	     			   app_cp_token,
				   seq_no);
		util_log_structinfo_free(app_cp_token);
		app_cp_token = NULL;
	}
}


/**
 * IMPLEMENTATION NOTE: Does not use oplen and op args.
 */
static void
echo_server_exec_ro(const uint32_t     client_id, 
		    const uint32_t     req_id, 
		    const uint32_t     oplen,
		    const uint8_t     *operation)
{
	printf("@%s> Invoked!\n", __func__); 
	char *tmp = calloc(1024, 1);
	assert(tmp);
	snprintf(tmp, 1024, "current seqno is %" PRIx32, st->next_seqno);
	StructInfo *result = util_log_structinfo_new(strlen(tmp));
	for (int i = 0; i < result->len; i++) 
		result->buf[i] = tmp[i];
	sshm_ssi_read_only_result(st->sbn,
			          result,
			          client_id,
			          req_id);

	// Clean up
	util_log_structinfo_free(result);
	result = NULL;
	CLEAN(tmp);
}

static void
echo_server_load_cp(const uint32_t	app_cp_token_len,
		    const uint8_t	*app_cp_token, 
		    const seqno_t	seq_no)
{
	assert(st);
	st->next_seqno = seq_no;
}

static void
echo_server_release_cp(const StructInfo *app_cp_token)
{
	// no-op
}

static void
echo_server_fetch_state(const StructInfo *state_token)
{
	// no-op
}

static void
echo_server_load_state(const StructInfo *state_token, 
		       const StructInfo *state)
{
	// no-op
}

static void
echo_server_init_glue(GlueShimInterface *g) 
{
	g->sshm_gsi_exec        = echo_server_exec;
	g->sshm_gsi_exec_ro     = echo_server_exec_ro;
	g->sshm_gsi_load_cp     = echo_server_load_cp;
	g->sshm_gsi_release_cp  = echo_server_release_cp;
	g->sshm_gsi_fetch_state = echo_server_fetch_state;
	g->sshm_gsi_load_state  = echo_server_load_state;
}

void
write_cb(struct bufferevent *bev,
	 void *ctx)
{
	printf("@%s> Write event\n", __func__); 
}

/*
 * The read callback (@todo Shouldn't *this* contain the logic of the
 * application? which is: extract the payload, put it in the reply, send it.)
 */
void 
read_cb(struct bufferevent *bev, 
	void               *ctx)
{
	printf("@%s> Read event\n", __func__); 
	uint8_t data[BUFSIZE];
	int fetched = bufferevent_read(bev, data, BUFSIZE);
	StructInfo *msg = bn_strip_markers(data);

	printf("@%s> MAM (fetched %d bytes)\n", __func__, fetched); 
	util_keygen_print_hex(msg->buf, msg->len);
	sshm_sbn_handle(msg, st->sbn->ml->mac, st->sbn);

	util_log_structinfo_free(msg);
	msg = NULL;
}

// This routine is very bad-written. FIXME
void
event_cb(struct bufferevent *bev, 
	 short		     what, 
	 void		    *ctx)
{
	//evutil_socket_t *fd = ctx;

	if (what & BEV_EVENT_READING) {
		printf("@%s> While reading:\n", __func__); 
		goto type;
	} else if (what & BEV_EVENT_WRITING) {
		printf("@%s> While writing:\n", __func__); 
		goto type;
	}

	if (what & BEV_EVENT_ERROR)
		printf("@%s> %s\n", 
		       __func__, 
		       evutil_socket_error_to_string(EVUTIL_SOCKET_ERROR())); 
	goto end;

type:	if (what & BEV_EVENT_CONNECTED)
		printf("@%s> Requested connection finished.\n", __func__); 
	if (what & BEV_EVENT_EOF)
		printf("@%s> End of file\n", __func__); 


end:	bufferevent_free(bev);
}

void
echo_dispatch(evutil_socket_t   sock, 
	      short	        what, 
	      void	       *arg)
{
	struct event_base *base = arg;

	struct sockaddr_storage senderaddr;
	socklen_t senderaddrlen = 0;

	evutil_socket_t fd = accept(sock,
				    (struct sockaddr *)&senderaddr,
				    &senderaddrlen);

	if (fd < 0) {
		perror("accept");
	} else if (fd > FD_SETSIZE) {
		close(fd);
	} else {
		printf("@%s> Accepted connection from port %d\n", 
		       __func__, 
		       ntohs(((struct sockaddr_in *)&senderaddr)->sin_port)); 

		struct bufferevent *bev;
		evutil_make_socket_nonblocking(fd);
		bev = bufferevent_socket_new(base, fd, BEV_OPT_CLOSE_ON_FREE);
		assert(bev);
		bufferevent_setcb(bev, 
				  read_cb, 
				  write_cb, 
				  event_cb, 
				  (evutil_socket_t *)&fd);
		bufferevent_setwatermark(bev, EV_READ, 0, (size_t)MAX_LINE);
		bufferevent_enable(bev, EV_READ|EV_WRITE);
	}
}

/**
 * The server application.
 */
int 
main(int   argc, 
     char *argv[])
{
	// Check input args.
	if (argc != 3) {
		fprintf(stderr, 
			"Usage: %s <id> <config_file>\n",
			argv[0]); 
		exit(EXIT_FAILURE);
	}

	// Initialize parameters from configuration.
	const char *config = argv[2];
	prm_init(config, &parms);
	
	// Initialize OpenSSL library's digests.
	OpenSSL_add_all_digests();

	// Initialize app state.
	st = calloc(1, sizeof(EchoServerState));
	st->next_seqno = 0;
	st->count = 0;
	st->loss_rate = 0;
	st->done_once = false;


	// Initialize glue.
	GlueShimInterface *glue = calloc(1, sizeof(GlueShimInterface));
	if (!glue) {
		fprintf(stderr, 
			"@%s> Problem allocating memory for the glue\n", 
			__func__);
		exit(EXIT_FAILURE);
	}
	echo_server_init_glue(glue);

	// Initialize and start the server.
	node_id_t server_id = node_id_from_string(argv[1]);
	st->sbn = sshm_sbn_new(argv[2], KEYS_DIR, server_id, NULL, glue);
	sshm_sbn_start(st->sbn);
	
	// TODO The following stuff should be done in the base node, not here in the
	// app.
	
	// Create an event base
	struct event_base *base; 
	base = event_base_new();
	assert(base);
	

	// Read the values of IP addresses and ports from config.
	RichSocket **listening_interfaces = cfg_get_listening_interfaces(config, EXEC, server_id);
	
	for (int i = 0; i < 4; i++)
		printf("Listening on %lu:%d\n", listening_interfaces[i]->ip, listening_interfaces[i]->port); 

	// Setup sockets and wrap them as requests.
	struct event *requests[NUMSOCKETS];
	for (int i = 0; i < NUMSOCKETS; i++) {

		char *ip_as_string = ip_to_string(listening_interfaces[i]->ip);
		char *port_as_string = port_to_string(listening_interfaces[i]->port);

		listening_interfaces[i]->sock_num = socket(PF_INET, SOCK_STREAM, IPPROTO_TCP);

		int one = 1;
		setsockopt(listening_interfaces[i]->sock_num, SOL_SOCKET, SO_REUSEADDR, &one, sizeof(one));

		struct addrinfo *rcvinfo;
		getaddrinfo(ip_as_string, port_as_string, NULL, &rcvinfo);
		if (bind(listening_interfaces[i]->sock_num, rcvinfo->ai_addr, rcvinfo->ai_addrlen) == -1) {
			perror("Problem when receiving - bind");
			freeaddrinfo(rcvinfo);
			exit(EXIT_FAILURE);
		}
		if (listen(listening_interfaces[i]->sock_num, 5) == -1) {
			perror("Problem when receiving - listen");
			freeaddrinfo(rcvinfo);
			exit(EXIT_FAILURE);
		}
		printf("@%s> Listening on: %s:%s (socket #%d)\n", 
		       __func__, 
		       ip_as_string, 
		       port_as_string, 
		       listening_interfaces[i]->sock_num);

		requests[i] = event_new(base, 
					listening_interfaces[i]->sock_num, 
					EV_READ|EV_PERSIST, 
					echo_dispatch, 
					(void *)base);
		event_add(requests[i], NULL);
		//printf("@%s> Added event to the base\n", __func__); 
		free(rcvinfo);
		CLEAN(ip_as_string);
		CLEAN(port_as_string);
	}

	event_base_dispatch(base);
	
	// Clean up.
	if (base) event_base_free(base);
	base = NULL;
	for (int i = 0; i < NUMSOCKETS; i++) 
		event_free(requests[i]);
	sshm_sbn_free(st->sbn);
	st->sbn = NULL;
	CLEAN(st);
	EVP_cleanup();
	return EXIT_SUCCESS;
}

