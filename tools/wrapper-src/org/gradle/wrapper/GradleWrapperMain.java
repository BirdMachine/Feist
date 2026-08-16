package org.gradle.wrapper;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Tiny self-bootstrapping Gradle launcher used only because this build sandbox cannot
 * download the official wrapper JAR. Once Gradle runs, `./gradlew wrapper` may replace
 * this with the official Gradle wrapper if desired.
 */
public final class GradleWrapperMain {
    private static final Pattern VERSION = Pattern.compile("gradle-([0-9.]+)-bin\\.zip");

    public static void main(String[] args) throws Exception {
        File appHome = new File(System.getProperty("birdie.appHome", System.getProperty("user.dir"))).getCanonicalFile();
        File propsFile = new File(appHome, "gradle/wrapper/gradle-wrapper.properties");
        Properties props = new Properties();
        try (InputStream in = new FileInputStream(propsFile)) { props.load(in); }
        String distributionUrl = props.getProperty("distributionUrl");
        if (distributionUrl == null) throw new IllegalStateException("distributionUrl missing from " + propsFile);

        Matcher matcher = VERSION.matcher(distributionUrl);
        if (!matcher.find()) throw new IllegalStateException("Cannot infer Gradle version from " + distributionUrl);
        String version = matcher.group(1);

        Path base = Path.of(System.getProperty("user.home"), ".gradle", "wrapper", "dists", "birdie-bootstrap", version);
        Path gradleHome = base.resolve("gradle-" + version);
        boolean windows = System.getProperty("os.name", "").toLowerCase().contains("win");
        Path executable = gradleHome.resolve("bin").resolve(windows ? "gradle.bat" : "gradle");

        if (!Files.exists(executable)) {
            Files.createDirectories(base);
            Path zip = base.resolve("gradle-" + version + "-bin.zip");
            if (!Files.exists(zip) || Files.size(zip) < 1_000_000L) {
                Path temp = base.resolve("gradle-download.tmp");
                System.err.println("Birdie wrapper: downloading Gradle " + version + " …");
                download(distributionUrl, temp);
                Files.move(temp, zip, StandardCopyOption.REPLACE_EXISTING);
            }
            deleteTree(gradleHome);
            unzip(zip, base);
            if (!windows) executable.toFile().setExecutable(true, false);
        }

        List<String> command = new ArrayList<>();
        command.add(executable.toString());
        for (String arg : args) command.add(arg);
        ProcessBuilder builder = new ProcessBuilder(command);
        builder.directory(appHome);
        builder.inheritIO();
        int code = builder.start().waitFor();
        System.exit(code);
    }

    private static void download(String source, Path target) throws Exception {
        URL current = URI.create(source).toURL();
        for (int redirects = 0; redirects < 10; redirects++) {
            HttpURLConnection connection = (HttpURLConnection) current.openConnection();
            connection.setInstanceFollowRedirects(false);
            connection.setConnectTimeout(20_000);
            connection.setReadTimeout(60_000);
            connection.setRequestProperty("User-Agent", "BirdiePhotoMaid-GradleWrapper/1");
            int code = connection.getResponseCode();
            if (code >= 300 && code < 400) {
                String location = connection.getHeaderField("Location");
                connection.disconnect();
                if (location == null) throw new IllegalStateException("Redirect without Location from " + current);
                current = current.toURI().resolve(location).toURL();
                continue;
            }
            if (code < 200 || code >= 300) throw new IllegalStateException("Gradle download failed: HTTP " + code);
            try (InputStream in = new BufferedInputStream(connection.getInputStream());
                 BufferedOutputStream out = new BufferedOutputStream(new FileOutputStream(target.toFile()))) {
                byte[] buffer = new byte[128 * 1024];
                int read;
                while ((read = in.read(buffer)) >= 0) out.write(buffer, 0, read);
            } finally {
                connection.disconnect();
            }
            return;
        }
        throw new IllegalStateException("Too many redirects while downloading Gradle");
    }

    private static void unzip(Path zipFile, Path destination) throws Exception {
        Path normalizedDestination = destination.toAbsolutePath().normalize();
        try (ZipInputStream in = new ZipInputStream(new BufferedInputStream(new FileInputStream(zipFile.toFile())))) {
            ZipEntry entry;
            while ((entry = in.getNextEntry()) != null) {
                Path output = normalizedDestination.resolve(entry.getName()).normalize();
                if (!output.startsWith(normalizedDestination)) throw new IllegalStateException("Unsafe zip path: " + entry.getName());
                if (entry.isDirectory()) {
                    Files.createDirectories(output);
                } else {
                    Files.createDirectories(output.getParent());
                    try (BufferedOutputStream out = new BufferedOutputStream(new FileOutputStream(output.toFile()))) {
                        byte[] buffer = new byte[128 * 1024];
                        int read;
                        while ((read = in.read(buffer)) >= 0) out.write(buffer, 0, read);
                    }
                }
                in.closeEntry();
            }
        }
    }

    private static void deleteTree(Path root) throws Exception {
        if (!Files.exists(root)) return;
        try (var stream = Files.walk(root)) {
            stream.sorted((a, b) -> b.getNameCount() - a.getNameCount()).forEach(path -> {
                try { Files.deleteIfExists(path); } catch (Exception ignored) { }
            });
        }
    }
}
