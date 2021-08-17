#include <stdio.h>
#include <stdlib.h>
#include <time.h>
#include <math.h>
#include <mpi.h>
#include <malloc.h>

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

int main(int argc, char** argv) {
    int a[AROW][ACOL];
    int* c[AROW];
    int i,j;
    int size, rank;
    
    int myRow[ACOL];
    MPI_Status status;
    
    MPI_Init(&argc, &argv);
    
    MPI_Comm_size(MPI_COMM_WORLD, &size);
    MPI_Comm_rank(MPI_COMM_WORLD, &rank);
    
    struct timeval start, end;

    if (size < AROW) {
        if (rank == 0) {
            printf("Error: There must be atleast %d sortors to run the application\n", AROW);
        }
        MPI_Finalize();
        exit(0);
    }

    if (rank >= AROW) {
       /* extra people! */
       MPI_Finalize();
       exit(0);
    } 
    
    if (rank == 0) {
        /* Generating Random Values for A & B Array*/
        srand(time(NULL));
        for (i = 0; i < AROW; i++) {
            for (j = 0;j < ACOL; j++) {
                a[i][j] = rand() % MAX_VALUE;
            }
        }
        
        /* start the timer */
        gettimeofday(&start, NULL);
        
        /* distributing data */
        for (i = 1; i < AROW; i++) {
            MPI_Send(a[i], ACOL, MPI_INT, i, 0, MPI_COMM_WORLD);
        }

        /* do root's own sorting */
        c[0] = sort(a[0]);
        for (i = 1; i < AROW; i++) {
            c[i] = (int*) malloc (sizeof(int) * ACOL);
            /* get others' sorted results */
            MPI_Recv(c[i], ACOL, MPI_INT, i, 0, MPI_COMM_WORLD, &status);
        }
        
        //sort_full(c);
        gettimeofday(&end, NULL);
        
        /* now print result and timing data */
        
        //printf("\nRESULT :\n");
        //for (i = 0; i < AROW; i++) {
        //    for (j = 0; j < ACOL; j++) {
        //        printf("%3d ", c[i][j]);
        //    }
        //    printf("\n");
        //}
        printf("\n\n");
        
        if (rank == 0) {
            long duration_ms = ((end.tv_sec  - start.tv_sec) * 1000 + (end.tv_usec - start.tv_usec) / 1000.0);
            printf("Time: %ld ms\n", duration_ms);
        }
    } else {
        /* get data from root */
        MPI_Recv(myRow, ACOL, MPI_INT, 0, 0, MPI_COMM_WORLD, &status);
        /* sort and send to root */
        int* result = sort(myRow);
        MPI_Send(result, ACOL, MPI_INT, 0, 0, MPI_COMM_WORLD);
    }
    
    MPI_Finalize();
    
    return 0;
}
