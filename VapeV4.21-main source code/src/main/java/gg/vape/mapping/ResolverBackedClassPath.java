package gg.vape.mapping;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import javassist.ClassPath;
import javassist.NotFoundException;

/*
 * ClassPath backed by MappingClassBytecodeResolver instead of raw loader
 * resources. Raw getResourceAsStream on some launchers returns aliased
 * (Notch) bytes for remapped (MCP) paths, which poisons Javassist with
 * name-mismatched classes. The resolver only returns bytes whose internal
 * name matches, falling back to JVMTI-captured defined bytecode.
 */
public class ResolverBackedClassPath
implements ClassPath {
    private final MappingClassBytecodeResolver resolver;

    public ResolverBackedClassPath(MappingClassBytecodeResolver resolver) {
        this.resolver = resolver;
    }

    @Override
    public InputStream openClassfile(String classname) throws NotFoundException {
        URL url = this.find(classname);
        if (url == null) {
            throw new NotFoundException(classname);
        }
        try {
            return url.openStream();
        }
        catch (IOException exception) {
            throw new NotFoundException(classname);
        }
    }

    @Override
    public URL find(String classname) {
        byte[] bytes = this.resolver == null ? null : this.resolver.y(classname);
        if (bytes == null) {
            return null;
        }
        try {
            return new URL(null, "resolverbytecode:" + classname,
                    new BytesHandler(bytes));
        }
        catch (Exception exception) {
            return null;
        }
    }

    private static final class BytesHandler
    extends URLStreamHandler {
        private final byte[] bytes;

        BytesHandler(byte[] bytes) {
            this.bytes = bytes;
        }

        @Override
        protected URLConnection openConnection(URL url) {
            final byte[] data = this.bytes;
            return new URLConnection(url) {
                @Override
                public void connect() {
                }

                @Override
                public InputStream getInputStream() {
                    return new ByteArrayInputStream(data);
                }
            };
        }
    }
}
