#include <stdio.h>
#include <stdlib.h>
#include <time.h>
#include <math.h>
 
#define AROW 3
#define ACOL 16
#define MAX_VALUE 10

int main(int argc, char *argv[]) {
    int a[AROW][ACOL];
    int b[ACOL];
    int c[AROW];
    int i,j;
    struct timeval start, end;
    srand(time(NULL));
    for (i = 0; i < AROW; i++) {
        for (j = 0; j < ACOL; j++) {
            if (i == 0) b[j] = rand() % MAX_VALUE;
            a[i][j] = rand() % MAX_VALUE;
        }
    }
    for (i = 0; i < AROW; i++) {
        c[i] = 0;
    }
    gettimeofday(&start, NULL);
    #pragma omp parallel for
    for (i = 0; i < AROW; i++) {
        for (j = 0; j < ACOL; j++) {
            c[i] += a[i][j] * b[j];
        }
    }
    gettimeofday(&end, NULL);
    for (i = 0; i < AROW; i++) {
        printf("%3d ", c[i]);
    }
    printf("\n");
    long duration_ms = ((end.tv_sec  - start.tv_sec) * 1000 + (end.tv_usec - start.tv_usec) / 1000.0);
    printf("Time: %ld ms\n", duration_ms);
    return 0;
}

