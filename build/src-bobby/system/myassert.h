#ifndef _MYASSERT_H_
#define  _MYASSERT_H_
#ifdef assert
#undef assert
#endif

// ----------- ASSERT
#ifdef TESTING 
void _assert(byte condition, byte fn, int ln);
# define assert(x) _assert(x, FILENUM, __LINE__)
#else
# ifdef BBSIM
   void _myassert(char* file, int ln, char* cond);
#  define assert(e) ((e) ? (void)0 : _myassert(__FILE__, __LINE__, #e))
# else 
#  define assert(x)
# endif
#endif

#endif
