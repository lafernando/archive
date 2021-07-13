function fib(int n) (int x) {
  if (n == 1 || n == 2) {
    return 1;
  } else {
    return fib(n - 1) + fib (n - 2);
  }
}

function loopTest (int count, int x) (int j, int k) {
    int a = 0;
    int b = 0;
    int i = 0;
    while (i < count) {
        a = a + x;
        b = b + x * 2;
        i = i + 1;
    }
    return a, b;
}

function smallOp(int a, int b, int c) (float x) {
    return a * b / c;
}
