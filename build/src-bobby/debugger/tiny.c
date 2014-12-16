/*
 * tiny.c - A simple, iterative HTTP/1.0 Web server that uses the 
 *     GET method to serve static and dynamic content.
 */
#include "csapp.h"
#include "./logger/mylogger.h"
#include <string>
#include <iostream>
#include <sstream>
#define DEBUG 2 
#define DYNAMIC 1
#define STATIC 0

using namespace std;

void doit(int fd);
void read_requesthdrs(rio_t *rp);
int parse_uri(char *uri, char *filename, char *cgiargs);
void process_command(int fd,char *command);
void serve_static(int fd, char *filename, int filesize);
void get_filetype(char *filename, char *filetype);
void serve_dynamic(int fd, char *filename, char *cgiargs);
void clienterror(int fd, char *cause, char *errnum, 
		char *shortmsg, char *longmsg);

void *thread(void *vargp);

void serve_json(int fd,char *content);

void send_json(int fd,char *content);


std::string log_message;

extern char* portname;
extern string defaultportname;
extern int baudrate;
extern volatile uint8_t resp_rxed;
extern volatile uint8_t tree_count;

int tree_cnt;


string  print_json(string key,string value) {
	std::stringstream ss;
	ss << "{\"" << key<<"\":\""<<value<<"\"}";
	string str = ss.str();
	return str;
}

int main(int argc, char **argv) 
{
	cout<<print_json("Name","Mihir")<<endl;
	///sem_init(&mutex, 0, 1);

	pthread_t tid;

	//printf("<================%s\n",__FUNCTION__);
	int listenfd, connfd, port, clientlen;
	struct sockaddr_in clientaddr;

	/* Check command line args */
	if (argc != 2) {
		fprintf(stderr, "usage: %s <port>\n", argv[0]);
		exit(1);
	}
	port = atoi(argv[1]);

	//Pthread_join(tid, NULL);

	listenfd = Open_listenfd(port);

	portname = (char*) defaultportname.c_str();
	cout<<"portname"<<portname;

	if( !Chunk::initSerial(portname, baudrate)  ) {
		exit(1);
	}

	portname = (char*) defaultportname.c_str();
	cout<<"portname"<<portname;

	sendIAmHost();
	//receiveLogs();


	Pthread_create(&tid, NULL, thread, NULL);
	//printf("reached line %d",__LINE__);
	while (1) {
		//printf("------------\n");
		clientlen = sizeof(clientaddr);
		connfd = Accept(listenfd, (SA *)&clientaddr, (socklen_t *)&clientlen);
		doit(connfd);
		Close(connfd);
		//printf("------------\n");
	}
	//printf("================>\n",__FUNCTION__);
}
/* $end tinymain */

/*
 * doit - handle one HTTP request/response transaction
 */
/* $begin doit */
void doit(int fd) 
{
	//printf("<================%s\n",__FUNCTION__);
	int content_type;
	struct stat sbuf;
	char buf[MAXLINE], method[MAXLINE], uri[MAXLINE], version[MAXLINE];
	char filename[MAXLINE], cgiargs[MAXLINE];

	rio_t rio;

	/* Read request line and headers */
	Rio_readinitb(&rio, fd);
	Rio_readlineb(&rio, buf, MAXLINE);
	sscanf(buf, "%s %s %s", method, uri, version);
	//printf("HTTP REQUEST -> %s\n",buf);
	if (strcasecmp(method, "GET")) { 
		clienterror(fd, method, "501", "Not Implemented",
				"Tiny does not implement this method");
		return;
	}
	read_requesthdrs(&rio);

	/* Parse URI from GET request */
	content_type = parse_uri(uri, filename, cgiargs);
	//printf("filename = %s\n",filename);
	//printf("cgiargs = %s\n",cgiargs);
	//printf("content_type = %d",content_type);
	if(content_type != 2) {//incase of debugging commands filename doesn't come into
		//the picture
		if (stat(filename, &sbuf) < 0) {
			clienterror(fd, filename, "404", "Not found",
					"Tiny couldn't find this file");
			return;
		}
	}
	switch (content_type) {
		case STATIC:	{ /* Serve static content */
					if (!(S_ISREG(sbuf.st_mode)) || !(S_IRUSR & sbuf.st_mode)) {
						clienterror(fd, filename, "403", "Forbidden",
								"Tiny couldn't read the file");
						return;
					}
					serve_static(fd, filename, sbuf.st_size);
					break;
				}
		case DYNAMIC: { /* Serve dynamic content */
				      if (!(S_ISREG(sbuf.st_mode)) || !(S_IXUSR & sbuf.st_mode)) {
					      clienterror(fd, filename, "403", "Forbidden",
							      "Tiny couldn't run the CGI program");
					      return;
				      }
				      serve_dynamic(fd, filename, cgiargs);
				      break;
			      }
		case DEBUG: {/* Serve the  debug information */
				    //printf("debug case\n");
				    process_command(fd,cgiargs);

				    break;
			    }
		default:
			    printf("error: No such service");
	}
	//printf("================>\n",__FUNCTION__);
}
/* $end doit */

/*
 * read_requesthdrs - read and parse HTTP request headers
 */
/* $begin read_requesthdrs */
void read_requesthdrs(rio_t *rp) 
{
	//printf("<================%s\n",__FUNCTION__);
	char buf[MAXLINE];

	Rio_readlineb(rp, buf, MAXLINE);
	//printf("cat %s", buf);
	while(strcmp(buf, "\r\n")) {
		//printf("~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n");
		Rio_readlineb(rp, buf, MAXLINE);
		//printf("!!!!!!!!!!!!!!!!!!!%s:%d %s\n",__FUNCTION__,__LINE__, buf);
		//printf("************************************************************************************\n");
	}
	//printf("^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^\n");
	return;
	//printf("================>\n",__FUNCTION__);
}
/* $end read_requesthdrs */

/*
 * parse_uri - parse URI into filename and CGI args
 *             return 0 if dynamic content, 1 if static
 */
/* $begin parse_uri */
int parse_uri(char *uri, char *filename, char *cgiargs) 
{
	//printf("<================%s\n",__FUNCTION__);
	char *ptr;
	//printf("uri = %s\n",uri);

	if (strstr(uri, "cgi-bin")) { /*Dynamic Content*/
		//printf("dynamic content\n");
		ptr = index(uri, '?');
		if (ptr) {
			strcpy(cgiargs, ptr+1);
			*ptr = '\0';
		}
		else 
			strcpy(cgiargs, "");
		strcpy(filename, ".");
		strcat(filename, uri);
		return DYNAMIC;
	}
	else if (strstr(uri,"/a/")) {//accessing the debugging options of the server.
		//printf("debug content\n");
		//printf("trying to debug the blinky blocks\n");
		//
		ptr = strstr(uri,"/a/")+strlen("/a/");
		//printf("ptr = %s\n",ptr);
		//strcpy(cgiargs,"");
		strcpy(cgiargs,ptr);
		*ptr = '\0';
		strcpy(filename, uri);

		return DEBUG;

	}
	else /*Static Content*/
	{
		///printf("static content\n");

		strcpy(cgiargs, "");
		strcpy(filename, ".");
		strcat(filename, uri);
		if (uri[strlen(uri)-1] == '/')
			strcat(filename, "home.html");
		//printf("filename = %s\n",filename);
		return STATIC;


	}
	//printf("================>\n",__FUNCTION__);
}
/* $end parse_uri */

/*
 * serve_static - copy a file back to the client 
 */
/* $begin serve_static */
void serve_static(int fd, char *filename, int filesize) 
{
	//printf("<================%s\n",__FUNCTION__);
	int srcfd;
	char *srcp, filetype[MAXLINE], buf[MAXBUF];

	/* Send response headers to client */
	get_filetype(filename, filetype);
	sprintf(buf, "HTTP/1.0 200 OK\r\n");
	sprintf(buf, "%sServer: Tiny Web Server\r\n", buf);
	sprintf(buf, "%sContent-length: %d\r\n", buf, filesize);
	sprintf(buf, "%sContent-type: %s\r\n\r\n", buf, filetype);
	Rio_writen(fd, buf, strlen(buf));

	/* Send response body to client */
	srcfd = Open(filename, O_RDONLY, 0);
	srcp = (char *) Mmap(0, filesize, PROT_READ, MAP_PRIVATE, srcfd, 0);
	Close(srcfd);
	Rio_writen(fd, srcp, filesize);
	Munmap(srcp, filesize);
	//printf("================>\n",__FUNCTION__);
}

void serve_json(int fd,char *content)
{
	char *srcp, filetype[MAXLINE], buf[MAXBUF];
	strcpy(filetype, "text/plain");
	sprintf(buf, "HTTP/1.0 200 OK\r\n");
	sprintf(buf, "%sServer: Tiny Web Server\r\n", buf);
	sprintf(buf, "%sContent-length: %d\r\n", buf, strlen(content));
	sprintf(buf, "%sContent-type: %s\r\n\r\n", buf, filetype);
	Rio_writen(fd, buf, strlen(buf));
	Rio_writen(fd, content, strlen(content));
}


void send_json(int fd,char *content)
{
	char *srcp, filetype[MAXLINE], buf[MAXBUF];
	strcpy(filetype, "application/json");
	sprintf(buf, "HTTP/1.0 200 OK\r\n");
	sprintf(buf, "%sServer: Tiny Web Server\r\n", buf);
	sprintf(buf, "%sContent-length: %d\r\n", buf, strlen(content));
	sprintf(buf, "%sContent-type: %s\r\n\r\n", buf, filetype);
	Rio_writen(fd, buf, strlen(buf));
	Rio_writen(fd, content, strlen(content));
}

/*
 * get_filetype - derive file type from file name
 */
void get_filetype(char *filename, char *filetype) 
{
	//printf("<================%s\n",__FUNCTION__);
	if (strstr(filename, ".html"))
		strcpy(filetype, "text/html");
	else if (strstr(filename, ".gif"))
		strcpy(filetype, "image/gif");
	else if (strstr(filename, ".jpg"))
		strcpy(filetype, "image/jpeg");
	else
		strcpy(filetype, "text/plain");
	//printf("================>\n",__FUNCTION__);
}  
/* $end serve_static */

/*
 * serve_dynamic - run a CGI program on behalf of the client
 */
/* $begin serve_dynamic */
void serve_dynamic(int fd, char *filename, char *cgiargs) 
{
	//printf("<================%s\n",__FUNCTION__);
	char buf[MAXLINE], *emptylist[] = { NULL };

	/* Return first part of HTTP response */
	sprintf(buf, "HTTP/1.0 200 OK\r\n");
	Rio_writen(fd, buf, strlen(buf));
	sprintf(buf, "Server: Tiny Web Server\r\n");
	Rio_writen(fd, buf, strlen(buf));

	if (Fork() == 0) { /* child */
		/* Real server would set all CGI vars here */
		setenv("QUERY_STRING", cgiargs, 1); 
		Dup2(fd, STDOUT_FILENO);         /* Redirect stdout to client */
		Execve(filename, emptylist, environ); /* Run CGI program */
	}
	Wait(NULL); /* Parent waits for and reaps child */
	//printf("================>\n");
}
/* $end serve_dynamic */

/*
 * clienterror - returns an error message to the client
 */
/* $begin clienterror */
void clienterror(int fd, char *cause, char *errnum, 
		char *shortmsg, char *longmsg) 
{
	//printf("<================%s\n",__FUNCTION__);
	char buf[MAXLINE], body[MAXBUF];

	/* Build the HTTP response body */
	sprintf(body, "<html><title>Tiny Error</title>");
	sprintf(body, "%s<body bgcolor=""ffffff"">\r\n", body);
	sprintf(body, "%s%s: %s\r\n", body, errnum, shortmsg);
	sprintf(body, "%s<p>%s: %s\r\n", body, longmsg, cause);
	sprintf(body, "%s<hr><em>The Tiny Web server</em>\r\n", body);

	/* Print the HTTP response */
	sprintf(buf, "HTTP/1.0 %s %s\r\n", errnum, shortmsg);
	Rio_writen(fd, buf, strlen(buf));
	sprintf(buf, "Content-type: text/html\r\n");
	Rio_writen(fd, buf, strlen(buf));
	sprintf(buf, "Content-length: %d\r\n\r\n", (int)strlen(body));
	Rio_writen(fd, buf, strlen(buf));
	Rio_writen(fd, body, strlen(body));
	//printf("================>\n");
}

void *thread(void *vargp) /* Thread routine */
{
	//creating detached thread that is reaped automatically and resources are freed automatically.
	Pthread_detach(pthread_self());
	//mylogger(NULL,NULL);
	stringifyLogs();

	return NULL;
}

void process_command(int fd,char *command)
{
	resp_rxed = 0;
	//printf("%d:%s(%s)\n",__LINE__,__FUNCTION__,command);
	char *ptr;
	char  function[MAXLINE];
	int i;
	ptr = index(command, '?');
	if (ptr) {
		*ptr = '\0';
	}
	*function = '\0';
	strcat(function, command);
	//printf("function to perform = %s\n",function);

	if(!strcmp(function,"reset")){
		printf("Got reset command\n");
		sendResetCmd();

		receiveLogs();
		//sendColorCmd(4);

	}

	if(!strcmp(function,"register_read")){
		//send_read_register();

	}
	else if (!strcmp(function,"memory_read")) {
		//send_read_memory();
	}
	else if(!strcmp(function,"num_tree")){
		printf("Got the num_tree count\n");
		send_tree_count();
		printf("waiting\n");
		while(!resp_rxed);
		tree_count = 3;
		resp_rxed = 0;
		printf("response_rxed\n");
		string value;
		std::stringstream ss;
		printf("tree_cnt ==== %d\n",tree_count);
		ss << tree_count;
		cout <<"tree_count"<<tree_count<<endl;
		value = ss.str();
		cout<<print_json("count","2")<<endl;
		printf("tree_cnt ==== %d\n",tree_count);
		send_json(fd,(char *)print_json("count","2").c_str());
		
	}
	else if (!strcmp(function,"debug_logs")) {
		serve_json(fd,(char *)log_message.c_str());
		log_message = "";
	}

}	



/* $end clienterror */
