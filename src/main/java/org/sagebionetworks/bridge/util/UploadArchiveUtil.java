package org.sagebionetworks.bridge.util;

import java.io.File;

import com.google.common.io.Files;

import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import org.sagebionetworks.bridge.services.UploadArchiveService;

// Usage: play "run-main org.sagebionetworks.bridge.util.UploadArchiveUtil [encrypt/decrypt] [appId] [input file]
// [output file]"
public class UploadArchiveUtil {
    @SuppressWarnings("resource")
    public static void main(String[] args) throws Exception {
        // args / usage
        if (args.length != 4) {
            System.out.println(
                    "Usage: play \"run-main org.sagebionetworks.bridge.util.UploadArchiveUtil [encrypt/decrypt] " +
                            "[appId] [input filename] [output filename]\"");
            System.exit(1);
            return;
        }
        String method = args[0];
        String appId = args[1];
        String inFilename = args[2];
        String outFilename = args[3];

        // load spring beans
        ApplicationContext springCtx = new ClassPathXmlApplicationContext("application-context.xml");
        UploadArchiveService uploadArchiveService = springCtx.getBean(UploadArchiveService.class);

        // read input file
        File inFile = new File(inFilename);
        byte[] inData = Files.toByteArray(inFile);

        // encrypt / decrypt
        byte[] outData;
        switch (method) {
            case "encrypt":
                outData = uploadArchiveService.encrypt(appId, inData);
                break;
            case "decrypt":
                outData = uploadArchiveService.decrypt(appId, inData);
                break;
            default:
                throw new IllegalArgumentException(String.format("Invalid method %s", method));
        }

        // write output file
        File outFile = new File(outFilename);
        Files.write(outData, outFile);
    }
}
