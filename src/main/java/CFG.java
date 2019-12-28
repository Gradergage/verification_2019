
import guru.nidi.graphviz.attribute.*;
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
    private final Object payload;
    public MutableNode node;
    private final List<CFG> children;
    public List<CFG> lastRoutes = new ArrayList<>();
    public boolean returnStatement = false;
    public boolean conditionStatement = false;
    public boolean unclosedConditionStatement = false;

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

    private Object getPayload(ParseTree tree) {
        if (tree.getChildCount() == 0) {
            return tree.getPayload();
        } else {
            String ruleName = tree.getClass().getSimpleName().replace("Context", "");
            return Character.toLowerCase(ruleName.charAt(0)) + ruleName.substring(1);
        }
    }

    private static void walk(ParseTree tree, CFG ast) {

        if (tree.getChildCount() == 0) {
            new CFG(ast, tree);
        } else if (tree.getChildCount() == 1) {
            walk(tree.getChild(0), ast);
        } else if (tree.getChildCount() > 1) {

            for (int i = 0; i < tree.getChildCount(); i++) {

                CFG temp = new CFG(ast, tree.getChild(i));

                if (!(temp.payload instanceof Token)) {
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
        MutableGraph g = mutGraph("CFGscheme").setDirected(true);
        CFG ast = this;
        handle(ast, g);
        try {
            Graphviz.fromGraph(g).width(9000).render(Format.PNG).toFile(new File("scheme/scheme.png"));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void handle(CFG node, MutableGraph g) {
        for (CFG n : node.getChildren()) {
            if (n.payload instanceof String && n.payload.equals("classBodyDeclaration")) {
                handleMethodDeclaration(n, g, node);
                continue;
            }
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
            if (n.payload instanceof Token && ((Token) n.payload).getText().equals("return")) {
                String name = extractStatementText(block, g);
                block.node = mutNode(generateName()).add(Label.of(name.toString())).add(Shape.ELLIPSE);
                block.returnStatement = true;
                g.add(parent.node.addLink(block.node));
                break;
            }
            if (n.payload.equals("blockStatement") || n.payload.equals("statementExpression")) {
                if (lastNode.lastRoutes.size() > 1) {
                    handleBlockStatement(n, g, null);
                    for (CFG l : lastNode.lastRoutes)
                        if (!l.returnStatement)
                            g.add(l.node.addLink(n.node));
                } else if (lastNode.lastRoutes.size() == 1) {
                    handleBlockStatement(n, g, lastNode);
                    for (CFG l : lastNode.lastRoutes)
                        if (!l.returnStatement)
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
                if (((Token) n.payload).getText().equals("return")) {
                    String name = extractStatementText(block, g);
                    block.returnStatement = true;
                    block.node = mutNode(generateName()).add(Label.of(name.toString())).add(Shape.ELLIPSE);
                    g.add(parent.node.addLink(block.node));
                    break;
                }
            } else {
                String res = extractStatementText(block, g);
                System.out.println(res);

                block.node = mutNode(generateName()).add(Label.of(res));
                block.node.add(Shape.RECTANGLE);
                if (parent != null)
                    g.add(parent.node.addLink(block.node));
                break;
            }
        }
    }

    private void handleMethodDeclaration(CFG method, MutableGraph g, CFG parent) {
        StringJoiner result = new StringJoiner(" ");
        //  g.add(parent.node.addLink(method.node));
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
                continue;
            }
        }
        method.node = mutNode(generateName()).add(Label.of(result.toString())).add(Shape.ELLIPSE);
        g.add(method.node);

        for (CFG n : body.getChildren()) {
            if (n.payload instanceof String && n.payload.equals("blockStatements")) {
                handleBlockStatements(n, g, method);
            }
        }
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
            }
        }
        System.out.println(forName.toString());
        block.node = mutNode(generateName()).add(Label.of(forName.toString())).add(Shape.HEXAGON);
        g.add(parent.node.addLink(block.node));

        CFG lastNode = handleBlockStatements(body, g, block);
        g.add(lastNode.node.addLink(block.node));
    }

    private void handleConditionDeclaration(CFG block, MutableGraph g, CFG parent) {
        block.conditionStatement = true;
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
                    if (e.payload instanceof String && e.payload.equals("blockStatements")) {
                        bodyTrue = e;
                    }
                }
            }
            if (n.payload instanceof String && n.payload.equals("statement")) {
                for (CFG e : n.getChildren()) {
                    if (e.payload instanceof String && e.payload.equals("blockStatements")) {
                        if (shortIF) {
                            bodyTrue = e;
                        } else {
                            bodyFalse = e;
                        }
                    }
                }
            }
        }

        block.node = mutNode(generateName()).add(Label.of(forName.toString())).add(Shape.DIAMOND);
        if (parent != null)
            g.add(parent.node.addLink(block.node));
        CFG trueRoute = handleBlockStatements(bodyTrue, g, block);
        CFG falseRoute = null;
        if (!shortIF) {
            falseRoute = handleBlockStatements(bodyFalse, g, block);
        }
        List<CFG> routes = new ArrayList<>();
        MutableNode end = mutNode(generateName()).add(Label.of("")).add(Shape.POINT);

        if (!trueRoute.returnStatement)
            g.add(trueRoute.node.addLink(end));

        if (!shortIF) {
            if (!falseRoute.returnStatement) {
                falseRoute.node.addLink(falseRoute.node.linkTo(end));
                g.add(falseRoute.node);
            }
        } else {
            block.node.addLink(block.node.linkTo(end));
            g.add(block.node);
        }

        block.node = end;
        block.lastRoutes = routes;
        handle(block, g);
    }

    private String generateName() {
        return String.format("%s", counter++);
    }
}

