
import guru.nidi.graphviz.attribute.Label;
import guru.nidi.graphviz.attribute.Shape;
import guru.nidi.graphviz.engine.Format;
import guru.nidi.graphviz.engine.Graphviz;
import guru.nidi.graphviz.model.Graph;
import guru.nidi.graphviz.model.MutableGraph;
import guru.nidi.graphviz.model.MutableNode;
import guru.nidi.graphviz.model.Node;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.ParseTree;

import static guru.nidi.graphviz.model.Factory.*;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

import java.util.List;
import java.util.StringJoiner;

public class CFG {

    private static int counter = 0;
    /**
     * The payload will either be the name of the parser rule, or the token
     * of a leaf in the tree.
     */
    private final Object payload;
    public MutableNode node;
    /**
     * All child nodes of this AST.
     */
    private final List<CFG> children;
    public List<CFG> lastRoutes = new ArrayList<CFG>();

    public CFG(ParseTree tree) {
        this(null, tree);
    }

    private CFG(CFG ast, ParseTree tree) {
        this(ast, tree, new ArrayList<CFG>());
    }

    private CFG(CFG parent, ParseTree tree, List<CFG> children) {

        this.payload = getPayload(tree);
        this.children = children;
        if (payload instanceof Token)
            node = mutNode(((Token) payload).getText());
        else
            node = mutNode(String.valueOf(payload));

        if (parent == null) {
            // We're at the root of the AST, traverse down the parse tree to fill
            // this AST with nodes.
            walk(tree, this);
        } else {
            parent.children.add(this);
        }
    }

    public Object getPayload() {
        return payload;
    }

    public List<CFG> getChildren() {
        return new ArrayList<>(children);
    }

    // Determines the payload of this AST: a string in case it's an inner node (which
    // is the name of the parser rule), or a Token in case it is a leaf node.
    private Object getPayload(ParseTree tree) {
        if (tree.getChildCount() == 0) {
            // A leaf node: return the tree's payload, which is a Token.
            return tree.getPayload();
        } else {
            // The name for parser rule `foo` will be `FooContext`. Strip `Context` and
            // lower case the first character.
            String ruleName = tree.getClass().getSimpleName().replace("Context", "");
            return Character.toLowerCase(ruleName.charAt(0)) + ruleName.substring(1);
        }
    }

    // Fills this AST based on the parse tree.
    private static void walk(ParseTree tree, CFG ast) {

        if (tree.getChildCount() == 0) {
            // We've reached a leaf. We must create a new instance of an AST because
            // the constructor will make sure this new instance is added to its parent's
            // child nodes.
            new CFG(ast, tree);
        } else if (tree.getChildCount() == 1) {
            // We've reached an inner node with a single child: we don't include this in
            // our AST.
            walk(tree.getChild(0), ast);
        } else if (tree.getChildCount() > 1) {

            for (int i = 0; i < tree.getChildCount(); i++) {

                CFG temp = new CFG(ast, tree.getChild(i));

                if (!(temp.payload instanceof Token)) {
                    // Only traverse down if the payload is not a Token.
                    walk(tree.getChild(i), temp);
                }

            }
        }
    }

    @Override
    public String toString() {

        StringBuilder builder = new StringBuilder();

        CFG ast = this;
        List<CFG> firstStack = new ArrayList<>();
        firstStack.add(ast);
        List<List<CFG>> childListStack = new ArrayList<>();
        childListStack.add(firstStack);

        while (!childListStack.isEmpty()) {

            List<CFG> childStack = childListStack.get(childListStack.size() - 1);

            if (childStack.isEmpty()) {
                childListStack.remove(childListStack.size() - 1);
            } else {
                ast = childStack.remove(0);
                String caption;

                if (ast.payload instanceof Token) {
                    Token token = (Token) ast.payload;
                    caption = String.format("TOKEN[%s]", token.getText().replace("\n", "\\n"));
                } else {
                    caption = String.valueOf(ast.payload);// + " " + ast.payload.getClass().getName();
                }

                String indent = "";

                for (int i = 0; i < childListStack.size() - 1; i++) {
                    indent += (childListStack.get(i).size() > 0) ? "|  " : "   ";
                }

                builder.append(indent)
                        .append(childStack.isEmpty() ? "'- " : "|- ")
                        .append(caption)
                        .append("\n");

                if (ast.children.size() > 0) {
                    List<CFG> children = new ArrayList<>();
                    for (int i = 0; i < ast.children.size(); i++) {
                        children.add(ast.children.get(i));
                    }
                    childListStack.add(children);
                }
            }
        }

        return builder.toString();
    }

    public void toScheme() {
        MutableGraph g = mutGraph("example").setDirected(true);
        CFG ast = this;
        handle(ast, g);
        try {
            Graphviz.fromGraph(g).width(9000).render(Format.PNG).toFile(new File("example/ex1m.png"));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void handle(CFG node, MutableGraph g) {
        for (CFG n : node.getChildren()) {
//            if (node.payload instanceof String && node.payload.equals("methodHeader")) {
//                handleMethodDeclaration(n, g, node);
//                continue;
//            }

            if (n.payload instanceof String && n.payload.equals("classBodyDeclaration")) {
                handleMethodDeclaration(n, g, node);
                continue;
            }

//            if (n.payload instanceof String && n.payload.equals("blockStatements")) {
//                handleBlockStatements(n, g, node);
//                continue;
//            }
//            if (node.payload instanceof String && node.payload.equals("blockStatement")) {
//                handleBlockStatement(n, g, node);
//                continue;
//            }
            handle(n, g);
        }
    }

    private String extractStatementText(CFG block, MutableGraph g) {
        StringJoiner result = new StringJoiner(" ");
        for (CFG n : block.getChildren()) {
            if (n.payload instanceof Token) {
                String token = ((Token) n.payload).getText();
                if (!token.equals(";"))
                    result.add(((Token) n.payload).getText());
            } else {
                result.add(extractStatementText(n, g));
            }
        }
        return result.toString();
    }

    private CFG handleBlockStatements(CFG block, MutableGraph g, CFG parent) {

        CFG lastNode = parent;
        for (CFG n : block.getChildren()) {
            if (n.payload.equals("blockStatement") || n.payload.equals("statementExpression")) {

                if (lastNode.lastRoutes.size() > 1) {
                    handleBlockStatement(n, g, null);
                    for (CFG l : lastNode.lastRoutes)
                        g.add(l.node.addLink(n.node));
                } else if (lastNode.lastRoutes.size() == 1) {
                    handleBlockStatement(n, g, lastNode);
                    for (CFG l : lastNode.lastRoutes)
                        g.add(l.node.addLink(n.node));
                } else {
                    handleBlockStatement(n, g, lastNode);
                }

                lastNode = n;
            }
        }
        return lastNode;
    }

    private void handleBlockStatement(CFG block, MutableGraph g, CFG parent) {

        for (CFG n : block.getChildren()) {
            if (n.payload instanceof Token) {
                if (((Token) n.payload).getText().equals("for")) {
                    handleLoopDeclaration(block, g, parent);
                    break;
                }
                if (((Token) n.payload).getText().equals("if")) {
                    handleConditionDeclaration(block, g, parent);
                    break;
                }
            } else {
                String res = extractStatementText(block, g);
                System.out.println(res);
                block.node = mutNode(generateName()).add(Label.of(res)).add(Shape.RECTANGLE);
                if (parent != null)
                    g.add(parent.node.addLink(block.node));
                break;
            }
//
//            MutableNode node0 = mutNode(generateName()).add(Label.of(extractStatementText(n, g))).add(Shape.RECTANGLE);
//            g.add(parent.node.addLink(node0));
//            handle(block, g);
        }
        //    MutableNode node0 = mutNode(generateName()).add(Label.of(extractStatementText(block, g))).add(Shape.RECTANGLE);
    }

    private void handleMethodDeclaration(CFG method, MutableGraph g, CFG parent) {
        StringJoiner result = new StringJoiner(" ");
        // MutableNode node0 = mutNode(generateName()).add(Label.of("MethodDeclaration")).add(Shape.NONE);
        g.add(parent.node.addLink(method.node));
        CFG body = null;
        for (CFG n : method.getChildren()) {
            if (n.payload instanceof String && n.payload.equals("methodModifier")) {
                result.add(extractStatementText(n, g));
                continue;
            }
            if (n.payload instanceof String && n.payload.equals("methodHeader")) {
                result.add(extractStatementText(n, g));
                continue;
            }
            if (n.payload instanceof String && n.payload.equals("methodBody")) {
                body = n;
                //  handleBlockStatements(n, g, method);
                System.out.println("GotBody");
                continue;
            }
        }
        //  MutableNode node0 = mutNode(generateName()).add(Label.of(result.toString())).add(Shape.NONE);
        System.out.println(result);
        method.node = mutNode(generateName()).add(Label.of(result.toString())).add(Shape.NONE);
        g.add(parent.node.addLink(method.node));

        for (CFG n : body.getChildren()) {
            if (n.payload instanceof String && n.payload.equals("blockStatements")) {
                handleBlockStatements(n, g, method);
            }
        }
        //    handle(method, g);
    }

    private void handleLoopDeclaration(CFG block, MutableGraph g, CFG parent) {
        StringJoiner forName = new StringJoiner("; ");
        CFG body = null;
        for (CFG n : block.getChildren()) {
            if (n.payload instanceof String && !n.payload.equals("statement")) {
                forName.add(extractStatementText(n, g));
            }
            if (n.payload instanceof String && n.payload.equals("statement")) {
                for (CFG e : n.getChildren()) {
                    System.out.println(e.payload);
                    if (e.payload instanceof String && e.payload.equals("blockStatements")) {
                        body = e;
                    }
                }
                //    handle(n, g);
            }
            //    handle(n, g);
        }
        System.out.println(forName.toString());
        block.node = mutNode(generateName()).add(Label.of(forName.toString())).add(Shape.HEXAGON);
        g.add(parent.node.addLink(block.node));

        CFG lastNode = handleBlockStatements(body, g, block);
        g.add(lastNode.node.addLink(block.node));
        //     handle(block, g);
    }

    private void handleConditionDeclaration(CFG block, MutableGraph g, CFG parent) {
        StringJoiner forName = new StringJoiner("");
        CFG bodyTrue = null;
        CFG bodyFalse = null;
        boolean shortIF = true;
        for (CFG n : block.getChildren()) {
            if (n.payload instanceof String && n.payload.equals("expression")) {
                forName.add(extractStatementText(n, g));
                continue;
            }
            if (n.payload instanceof String && n.payload.equals("statementNoShortIf")) {
                shortIF = false;
                for (CFG e : n.getChildren()) {
                    //  System.out.println(e.payload);
                    if (e.payload instanceof String && e.payload.equals("blockStatements")) {
                        bodyTrue = e;
                    }
                }
            }
            if (n.payload instanceof String && n.payload.equals("statement")) {
                //   System.out.println(n.payload);
                System.out.println("Found false");
                for (CFG e : n.getChildren()) {
                    //    System.out.println(e.payload);
                    if (e.payload instanceof String && e.payload.equals("blockStatements")) {
                        if (shortIF) {
                            bodyTrue = e;
                        } else {
                            bodyFalse = e;
                        }
                    }
                }
            }
            //     handle(n, g);
        }

        block.node = mutNode(generateName()).add(Label.of(forName.toString())).add(Shape.DIAMOND);
        g.add(parent.node.addLink(block.node));
        CFG trueRoute = handleBlockStatements(bodyTrue, g, block);
        CFG falseRoute = null;
        if (!shortIF) {
            falseRoute = handleBlockStatements(bodyFalse, g, block);
        }
        List<CFG> routes = new ArrayList<>();

        routes.add(trueRoute);
        if (!shortIF)
            routes.add(falseRoute);
        block.lastRoutes = routes;
        handle(block, g);
    }

    public String toScheme2() {

        MutableGraph g = mutGraph("example").setDirected(false);
        StringBuilder builder = new StringBuilder();

        CFG ast = this;
        List<CFG> firstStack = new ArrayList<>();
        firstStack.add(ast);
        List<List<CFG>> childListStack = new ArrayList<>();
        childListStack.add(firstStack);
        while (!childListStack.isEmpty()) {

            List<CFG> childStack = childListStack.get(childListStack.size() - 1);

            if (childStack.isEmpty()) {
                childListStack.remove(childListStack.size() - 1);
            } else {
                ast = childStack.remove(0);
                String caption;

                if (ast.payload instanceof Token) {
                    Token token = (Token) ast.payload;


                    caption = String.format("%s", token.getText().replace("\n", "\\n"));
                } else {
                    caption = String.valueOf(ast.payload);
                }
                g.add(mutNode(caption));
                String indent = "";

                for (int i = 0; i < childListStack.size() - 1; i++) {
                    indent += (childListStack.get(i).size() > 0) ? "|  " : "   ";
                }

                builder.append(indent)
                        .append(childStack.isEmpty() ? "'- " : "|- ")
                        .append(caption)
                        .append("\n");

                if (ast.children.size() > 0) {
                    List<CFG> children = new ArrayList<>();
                    for (int i = 0; i < ast.children.size(); i++) {
                        children.add(ast.children.get(i));
                    }
                    childListStack.add(children);
                }
            }
        }
        return builder.toString();
    }

    public void example() {
      /*  MutableGraph g = mutGraph("example").setDirected(true);
        Node node1 = node("if");
        Node node2 = node("first block");
        Node node3 = node("second block");
        node1.link(node2);
        node1.link(node3);

        g.add(mutNode("test").add(node1).add(Shape.DIAMOND));
        g.add(mutNode("test").add(node2).add(Shape.RECTANGLE));
        g.add(mutNode("test").add(node3).add(Shape.RECTANGLE));*/
        MutableNode node0 = mutNode("i=0;i<5;i++").add(Shape.HEXAGON);
        MutableNode node1 = mutNode("a>0").add(Shape.DIAMOND);
        MutableNode node2 = mutNode("a*3").add(Shape.RECTANGLE);
        MutableNode node3 = mutNode("a*5").add(Shape.RECTANGLE);
        //node1.link(to(node2), to(node3));
        MutableGraph g = mutGraph("ex2").setDirected(true);
        //node1.linkTo(to(node2), to(node3));
        g.add(node0.addLink(node1));
        g.add(node1.addLink(node2));
        g.add(node1.addLink(node3));
        //  g.add(node2);
        //   g.add(node3);
        try {
            Graphviz.fromGraph(g).width(400).render(Format.PNG).toFile(new File("example/ex1m.png"));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private String HandleChild() {
        return null;
    }

    private String generateName() {
        return String.format("%s", counter++);
    }
}

