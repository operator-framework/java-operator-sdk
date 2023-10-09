package {{groupId}};

import io.javaoperatorsdk.operator.Operator;

public class Runner {

    public static void main(String[] args) {
        Operator operator = new Operator();
        operator.register(new {{artifactClassId}}Reconciler());
        operator.start();
    }
}
