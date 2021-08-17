#include <stdio.h>
#include <stdlib.h>
#include <time.h>
#include <math.h>
 
#define AROW 3
#define ACOL 10000
#define MAX_VALUE 10

int compare_ints (const void *a, const void *b) {
   const int *da = (const int *) a;
   const int *db = (const int *) b;
   return (*da > *db) - (*da < *db);
}

int* sort(int* row) {
    qsort(row, ACOL, sizeof(int), compare_ints);
    return row;
}

void merge(int m, int n, int k, int A[], int B[], int C[]) {
    int i = 0, j = 0, p;
    while (i < m && j < n) {
        if (A[i] <= B[j]) {
            C[k] = A[i];
            i++;
        } else {
            C[k] = B[j];
            j++;
        }
        k++;
    }
    if (i < m) {
        for (p = i; p < m; p++) {
             C[k] = A[p];
             k++;
        }
    } else {
        for (p = j; p < n; p++) {
             C[k] = B[p];
             k++;
        }
    }
}

int* sort_full(int** a) {
    int* C = (int*) malloc(sizeof(int) * AROW * ACOL);
    int N = AROW * ACOL;
    int i;
    for (i = 0; i < AROW; i++) {
        merge(i * ACOL, ACOL, i * ACOL, C, a[i], C);
    }
    return C;
}

int main(int argc, char *argv[]) {
    int a[AROW][ACOL];
    int i, j;
    struct timeval start, end;
    srand(time(NULL));
    for (i = 0; i < AROW; i++) {
        for (j = 0; j < ACOL; j++) {
            a[i][j] = rand() % MAX_VALUE;
        }
    }
    gettimeofday(&start, NULL);
    #pragma omp parallel for
    for (i = 0; i < AROW; i++) {
        sort(a[i]);
    }
    //sort_full(a);
    gettimeofday(&end, NULL);
    //for (i = 0; i < AROW; i++) {
    //    for (j = 0; j < ACOL; j++) {
    //        printf("%3d ", a[i][j]);
    //    }
    //    printf("\n");
    //}
    printf("\n");
    long duration_ms = ((end.tv_sec  - start.tv_sec) * 1000 + (end.tv_usec - start.tv_usec) / 1000.0);
    printf("Time: %ld ms\n", duration_ms);
    return 0;
}

