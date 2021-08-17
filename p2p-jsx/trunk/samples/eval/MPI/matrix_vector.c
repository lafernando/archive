#include <stdio.h>
#include <stdlib.h>
#include <time.h>
#include <math.h>
#include <mpi.h>
#include <malloc.h>

#define AROW 20
#define ACOL 100

#define MAX_VALUE 10

int process(int* row, int* x) {
    int result = 0;
    int i;
    for (i = 0; i < ACOL; i++) {
        result += row[i] * x[i];
    }
    return result;
}

void printMatrix(int rows, int cols, int* matrix) {
    int i, j;
    for (i = 0; i < rows; i++) {
        for (j = 0; j < cols; j++) {
            printf("%3d ", matrix[(cols * i) + j]);
        }
        printf("\n");
    }
}

int main(int argc, char** argv) {
    int* a;
    int b[ACOL];
    int* c;
    int i, j;
    int size, rank;
    
    int myRow[ACOL];
    MPI_Status status;
    
    MPI_Init(&argc, &argv);
    
    MPI_Comm_size(MPI_COMM_WORLD, &size);
    MPI_Comm_rank(MPI_COMM_WORLD, &rank);
    
    double start_time;

    if (size < AROW) {
        if (rank == 0) {
            printf("Error: There must be atleast %d processors to run the application\n", AROW);
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
        a = (int*) malloc (sizeof(int) * AROW * ACOL);
        /* generating random values for a & b array*/
        srand(time(NULL));
        for (i = 0; i < AROW; i++) {
            for (j = 0;j<ACOL; j++) {
                if (i == 0) b[j] = rand() % MAX_VALUE;
                a[(ACOL * i) + j] = rand() % MAX_VALUE;
            }
        }
        /* printing matrices */
        printf("Matrix A :\n");
        printMatrix(AROW, ACOL, a);

        printf("\nMatrix B :\n");
        printMatrix(1, ACOL, b);

        /* start the timer */
        start_time = MPI_Wtime();
    }    
        
    /* distributing/receive data */
    MPI_Scatter(a, ACOL, MPI_INT, myRow, ACOL, MPI_INT, 0, MPI_COMM_WORLD);
    MPI_Bcast(b, ACOL, MPI_INT, 0, MPI_COMM_WORLD);

    /* processing */
    int result = process(myRow, b);
    if (rank == 0) {
        c = (int*) malloc (sizeof(int) * AROW);
    }
    /* collect results */
    MPI_Gather(&result, 1, MPI_INT, c, 1, MPI_INT, 0, MPI_COMM_WORLD);
    if (rank == 0) { /* root */
        /* stop timer */
        double end_time = MPI_Wtime();
        /* now print result and timing data */
        printf("\nMatrix C :\n");
        printMatrix(1, AROW, c);
        printf("\n");
        printf("Time taken: %u ms\n", ((end_time - start_time) * 1000.0));
    }

    MPI_Finalize();
    
    return 0;
}
