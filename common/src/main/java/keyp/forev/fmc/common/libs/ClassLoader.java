package keyp.forev.fmc.common.libs;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import java.util.HashMap;
import keyp.forev.fmc.common.libs.interfaces.PackageManager;

public class ClassLoader {
    // メインクラスで、パッケージリストを読み込むときに
    // すべてのパッケージリストに対して、クラスローダーを作成する
    public static Map<PackageManager, URLClassLoader> classLoaders = new HashMap<>();
    private URLClassLoader urlClassLoader;
    public ClassLoader() {
        this.urlClassLoader = (URLClassLoader) java.lang.ClassLoader.getSystemClassLoader();
    }

    public CompletableFuture<List<Class<?>>> loadClassesFromJars(List<PackageManager> packages, Path dataDirectory) {
        List<CompletableFuture<Class<?>>> futures = packages.stream()
            .map(pkg -> {
                Path jarPath = dataDirectory.resolve("libs/" + getFileNameFromURL(pkg.getUrl()));
                return loadClassFromJar(jarPath, pkg.getClassName());
            })
            .collect(Collectors.toList());
        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
            .thenApply(v -> futures.stream()
                .map(CompletableFuture::join)
                .collect(Collectors.toList()));
    }

    // 仮に、パッケージが読み込まれたときに、
    // すべての使うクラスを読み込む必要はなくって、
    // 使うクラスを指定して読み込むことができるようにする
    // それがClassManagerの役割
    // 各パッケージが独自のクラスローダーを持てば、クラスの競合や依存関係の問題が回避できる
    public CompletableFuture<Class<?>> loadClassFromJar(Path jarPath, String className) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                addURLToClassLoader(jarPath.toUri().toURL());
                return urlClassLoader.loadClass(className);
            } catch (IOException | ClassNotFoundException e) {
                e.printStackTrace();
                return null;
            }
        });
    }

    private void addURLToClassLoader(URL url) throws IOException {
        URLClassLoader sysLoader = (URLClassLoader) java.lang.ClassLoader.getSystemClassLoader();
        URLClassLoader urlClassLoader = new URLClassLoader(new URL[]{url}, sysLoader);
        this.urlClassLoader = urlClassLoader;
        // ここで、VClassManagerで取得できるClass<?>が必要
        // Map<Class<?>, urlClassLoader>に格納できる
        //Class.forName("クラス名", true, urlClassLoader);
    }

    public URLClassLoader getUrlClassLoader() {
        return urlClassLoader;
    }

    private String getFileNameFromURL(URL url) {
        String urlString = url.toString();
        return urlString.substring(urlString.lastIndexOf('/') + 1);
    }
}