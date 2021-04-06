package comp0012.target;

public class BooleanOperators{
    public void test_or(){
        boolean one = true;
        boolean two = false;
        boolean x = one | two;
    }
    public void test_xor(){
        boolean one = true;
        boolean two = false;
        boolean x = one ^ two;
    }
    public void test_and(){
        boolean one = true;
        boolean two = false;
        boolean x = one && two;
    }

    public void test_sup(){
        float f1 = 56;
        float f2 = 67;
        boolean b1 = f1 > f2;
    }
    public void test_inf(){
        float f1 = 56;
        float f2 = 67;
        boolean b1 = f1 < f2;



    }
    public void test_if(){
        float f1 = 56;
        float f2 = 67;
        

        if (f1 > f2){
            
            int a = 0;
            
        }
        else{
            int b =4;
        }


    }
    public void test_minus(){
        int a = 5;
        int b = -a;
    }
}