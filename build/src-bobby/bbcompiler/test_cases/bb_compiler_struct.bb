// the bb compiler seems to have issues with multiple line threadtypes
threadtype typedef struct _Test
{
    int x;
} Test;

// this doesn't work either, as the struct definition
// needs to also be in localtypes.h
threadtype typedef struct _Test2 Test2;

struct _Test2
{
    int x;
} Test2;

int main ()
{
    return 0;
}