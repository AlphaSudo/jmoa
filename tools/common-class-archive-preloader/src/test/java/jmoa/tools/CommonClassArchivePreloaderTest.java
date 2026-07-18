package jmoa.tools;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CommonClassArchivePreloaderTest {
    @TempDir
    Path tempDir;

    @Test
    void readsSlashAndDotNamesAndIgnoresComments() throws Exception {
        Path list = tempDir.resolve("classes.txt");
        Files.write(list, List.of("# frozen", "java/lang/String", "", "java.util.List"));

        assertEquals(List.of("java.lang.String", "java.util.List"), CommonClassArchivePreloader.readClassNames(list));
    }

    @Test
    void rejectsRuntimeGeneratedFamilies() throws Exception {
        Path list = tempDir.resolve("classes.txt");
        Files.writeString(list, "example.Type$$Lambda$1/0x00000001");

        assertThrows(IllegalArgumentException.class, () -> CommonClassArchivePreloader.readClassNames(list));
    }
}
