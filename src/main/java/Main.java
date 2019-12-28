import org.antlr.v4.runtime.ANTLRFileStream;
import org.antlr.v4.runtime.BailErrorStrategy;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.DiagnosticErrorListener;
import org.antlr.v4.runtime.Lexer;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.atn.LexerATNSimulator;
import org.antlr.v4.runtime.atn.PredictionMode;

import java.io.File;
import java.io.FileNotFoundException;
import java.nio.file.*;
import java.util.Optional;

public class Main {
    public static void main(String[] args) {
        Parser parser = null;
        try {
            parser = new Parser("TestMainClass.java");
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        parser.parse();
    }

}
