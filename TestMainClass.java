public class TestMainClass {

    public static int methodAdd(int k) {
        k++;
        return k;
    }

    public void showit(int k) {
        System.out.println(k);
    }

    //main function
    public static void main(String[] args) {
        int a;
        int b;

        a = 0;
        b = 4;

        for (int i = 0; i < 10; i++) {
            a = methodAdd(a);
            a++;
            a = 1443;
        }

        b = 1123;
        if (b > 0) {
            b = a + b;
        }

        if (a > b) {
            a += 5;
            return 16;
        } else {
            b = 13;
        }

        if (a > 0) {
            b = a ^ b;
            if (a < 5) {
                b = 4;
            } else {
                a = 6;
            }
        } else {
            b = 13;
        }

        a = 535;
        showit(a);
        return 12;
    }
}
