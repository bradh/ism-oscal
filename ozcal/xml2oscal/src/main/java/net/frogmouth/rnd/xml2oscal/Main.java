package net.frogmouth.rnd.xml2oscal;

import java.io.IOException;
import java.net.URISyntaxException;

public class Main {

    /** @param args the command line arguments */
    public static void main(String[] args) throws URISyntaxException, IOException {
        Xml2Oscal converter = new Xml2Oscal();
        if (args.length > 3) {
            converter.extractE8Mappings(args[3]);
        }
        converter.readFrom(args[0]);
        converter.writeTo(args[1], args[2]);
    }
}
