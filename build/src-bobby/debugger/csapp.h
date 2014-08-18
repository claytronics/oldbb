/* $begin csapp.h */
#ifndef __CSAPP_H__
#define __CSAPP_H__

#include <stdio.h>
#include <stdlib.h>
#include <unistd.h>
#include <string.h>
#include <ctype.h>
#include <setjmp.h>
#include <signal.h>
#include <sys/time.h>
#include <sys/types.h>
#include <sys/wait.h>
#include <sys/stat.h>
#include <fcntl.h>
#include <sys/mman.h>
#include <errno.h>
#include <math.h>
#include <pthread.h>
#include <semaphore.h>
#include <sys/socket.h>
#include <netdb.h>
#include <netinet/in.h>
#include <arpa/inet.h>


/* Default file permissions are DEF_MODE & ~DEF_UMASK */
/* $begin createmasks */
#define DEF_MODE   S_IRUSR|S_IWUSR|S_IRGRP|S_IWGRP|S_IROTH|S_IWOTH
#define DEF_UMASK  S_IWGRP|S_IWOTH
/* $end createmasks */

/* Simplifies calls to bind(), connect(), and accept() */
/* $begin sockaddrdef */
typedef struct sockaddr SA;
/* $end sockaddrdef */

/* Persistent state for the robust I/O (Rio) package */
/* $begin rio_t */
#define RIO_BUFSIZE 8192
typedef struct {
    int rio_fd;                /* descriptor for this internal buf */
    int rio_cnt;               /* unread bytes in internal buf */
    char *rio_bufptr;          /* next unread byte in internal buf */
    char rio_buf[RIO_BUFSIZE]; /* internal buffer */
} rio_t;
/* $end rio_t */

/* extern "C" al variables */
extern "C"  int h_errno;    /* defined by BIND for DNS errors */ 
extern "C"  char **environ; /* defined by libc */

/* Misc constants */
#define	MAXLINE	 8192  /* max text line length */
#define MAXBUF   8192  /* max I/O buffer size */
#define LISTENQ  1024  /* second argument to listen() */

/* Our own error-handling functions */
extern "C"  void unix_error(char *msg);
extern "C"  void posix_error(int code, char *msg);
extern "C"  void dns_error(char *msg);
extern "C"  void app_error(char *msg);

/* Process control wrappers */
extern "C"  pid_t Fork(void);
extern "C"  void Execve(const char *filename, char *const argv[], char *const envp[]);
extern "C"  pid_t Wait(int *status);
extern "C"  pid_t Waitpid(pid_t pid, int *iptr, int options);
extern "C"  void Kill(pid_t pid, int signum);
extern "C"  unsigned int Sleep(unsigned int secs);
extern "C"  void Pause(void);
extern "C"  unsigned int Alarm(unsigned int seconds);
extern "C"  void Setpgid(pid_t pid, pid_t pgid);
extern "C"  pid_t Getpgrp();

/* Signal wrappers */
typedef void handler_t(int);
extern "C"  handler_t *Signal(int signum, handler_t *handler);
extern "C"  void Sigprocmask(int how, const sigset_t *set, sigset_t *oldset);
extern "C"  void Sigemptyset(sigset_t *set);
extern "C"  void Sigfillset(sigset_t *set);
extern "C"  void Sigaddset(sigset_t *set, int signum);
extern "C"  void Sigdelset(sigset_t *set, int signum);
extern "C"  int Sigismember(const sigset_t *set, int signum);

/* Unix I/O wrappers */
extern "C"  int Open(const char *pathname, int flags, mode_t mode);
extern "C"  ssize_t Read(int fd, void *buf, size_t count);
extern "C"  ssize_t Write(int fd, const void *buf, size_t count);
extern "C"  off_t Lseek(int fildes, off_t offset, int whence);
extern "C"  void Close(int fd);
extern "C"  int Select(int  n, fd_set *readfds, fd_set *writefds, fd_set *exceptfds, 
	   struct timeval *timeout);
extern "C"  int Dup2(int fd1, int fd2);
extern "C"  void Stat(const char *filename, struct stat *buf);
extern "C"  void Fstat(int fd, struct stat *buf) ;

/* Memory mapping wrappers */
extern "C"  void *Mmap(void *addr, size_t len, int prot, int flags, int fd, off_t offset);
extern "C"  void Munmap(void *start, size_t length);

/* Standard I/O wrappers */
extern "C"  void Fclose(FILE *fp);
extern "C"  FILE *Fdopen(int fd, const char *type);
extern "C"  char *Fgets(char *ptr, int n, FILE *stream);
extern "C"  FILE *Fopen(const char *filename, const char *mode);
extern "C"  void Fputs(const char *ptr, FILE *stream);
extern "C"  size_t Fread(void *ptr, size_t size, size_t nmemb, FILE *stream);
extern "C"  void Fwrite(const void *ptr, size_t size, size_t nmemb, FILE *stream);

/* Dynamic storage allocation wrappers */
extern "C"  void *Malloc(size_t size);
extern "C"  void *Realloc(void *ptr, size_t size);
extern "C"  void *Calloc(size_t nmemb, size_t size);
extern "C"  void Free(void *ptr);

/* Sockets interface wrappers */
extern "C"  int Socket(int domain, int type, int protocol);
extern "C"  void Setsockopt(int s, int level, int optname, const void *optval, int optlen);
extern "C"  void Bind(int sockfd, struct sockaddr *my_addr, int addrlen);
extern "C"  void Listen(int s, int backlog);
extern "C"  int Accept(int s, struct sockaddr *addr, socklen_t *addrlen);
extern "C"  void Connect(int sockfd, struct sockaddr *serv_addr, int addrlen);

/* DNS wrappers */
extern "C"  struct hostent *Gethostbyname(const char *name);
extern "C"  struct hostent *Gethostbyaddr(const char *addr, int len, int type);

/* Pthreads thread control wrappers */
extern "C"  void Pthread_create(pthread_t *tidp, pthread_attr_t *attrp, 
		    void * (*routine)(void *), void *argp);
extern "C"  void Pthread_join(pthread_t tid, void **thread_return);
extern "C"  void Pthread_cancel(pthread_t tid);
extern "C"  void Pthread_detach(pthread_t tid);
extern "C"  void Pthread_exit(void *retval);
extern "C"  pthread_t Pthread_self(void);
extern "C"  void Pthread_once(pthread_once_t *once_control, void (*init_function)());

/* POSIX semaphore wrappers */
extern "C"  void Sem_init(sem_t *sem, int pshared, unsigned int value);
extern "C"  void P(sem_t *sem);
extern "C"  void V(sem_t *sem);

/* Rio (Robust I/O) package */
extern "C"  ssize_t rio_readn(int fd, void *usrbuf, size_t n);
extern "C"  ssize_t rio_writen(int fd, void *usrbuf, size_t n);
extern "C"  void rio_readinitb(rio_t *rp, int fd); 
extern "C"  ssize_t	rio_readnb(rio_t *rp, void *usrbuf, size_t n);
extern "C"  ssize_t	rio_readlineb(rio_t *rp, void *usrbuf, size_t maxlen);

/* Wrappers for Rio package */
extern "C"  ssize_t Rio_readn(int fd, void *usrbuf, size_t n);
extern "C"  void Rio_writen(int fd, void *usrbuf, size_t n);
extern "C"  void Rio_readinitb(rio_t *rp, int fd); 
extern "C"  ssize_t Rio_readnb(rio_t *rp, void *usrbuf, size_t n);
extern "C"  ssize_t Rio_readlineb(rio_t *rp, void *usrbuf, size_t maxlen);

/* Client/server helper functions */
extern "C"  int open_clientfd(char *hostname, int portno);
extern "C"  int open_listenfd(int portno);

/* Wrappers for client/server helper functions */
extern "C"  int Open_clientfd(char *hostname, int port);
extern "C"  int Open_listenfd(int port); 

#endif /* __CSAPP_H__ */
/* $end csapp.h */
