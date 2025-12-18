package com.examplanner;

import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.fail;

public class ResourceBundleTest {

    @Test
    public void testBundleParity() throws IOException {
        Properties enProps = new Properties();
        enProps.load(Files.newInputStream(Paths.get("src/main/resources/com/examplanner/ui/messages_en.properties")));

        Properties trProps = new Properties();
        trProps.load(Files.newInputStream(Paths.get("src/main/resources/com/examplanner/ui/messages_tr.properties")));

        Set<String> enKeys = enProps.stringPropertyNames();
        Set<String> trKeys = trProps.stringPropertyNames();

        Set<String> missingInTr = new HashSet<>(enKeys);
        missingInTr.removeAll(trKeys);

        Set<String> missingInEn = new HashSet<>(trKeys);
        missingInEn.removeAll(enKeys);

        StringBuilder errorMsg = new StringBuilder();
        if (!missingInTr.isEmpty()) {
            errorMsg.append("Keys missing in Turkish bundle: ").append(missingInTr).append("\n");
        }
        if (!missingInEn.isEmpty()) {
            errorMsg.append("Keys missing in English bundle: ").append(missingInEn).append("\n");
        }

        if (errorMsg.length() > 0) {
            fail(errorMsg.toString());
        }
    }
}
