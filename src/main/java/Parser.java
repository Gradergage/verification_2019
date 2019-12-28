import org.antlr.v4.runtime.ANTLRFileStream;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.Lexer;
import org.antlr.v4.runtime.ParserRuleContext;
import parsers.Java8Lexer;
import parsers.Java8Parser;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;

public class Parser {
    private File source;

    public Parser(String path) throws FileNotFoundException {
        source = new File(path);
        if (!source.exists()) {
            System.out.println("File doesn't exist");
            throw new FileNotFoundException();
        }
    }

    public void parse() {
        try {
            Lexer lexer = new Java8Lexer(new ANTLRFileStream(source.getCanonicalPath()));
            CommonTokenStream tokens = new CommonTokenStream(lexer);
            Java8Parser parser = new Java8Parser(tokens);

            parser.setBuildParseTree(true);

            ParserRuleContext tree = parser.compilationUnit();
            CFG ast = new CFG(tree);


            ast.toScheme();

       //  System.out.println(ast);
        } catch (IOException e) {
            System.err.println("parser exception: " + e);
            e.printStackTrace();   // so we can get stack trace
        }
    }
}
