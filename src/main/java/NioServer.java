import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Iterator;
import java.util.Set;
import java.util.stream.Stream;

public class NioServer {
    public enum CommandType {
        LS("ls"), CD("cd "), CAT("cat "), EXIT("exit");

        private String command;

        public String getCommand() {
            return command;
        }

        CommandType(String command) {
            this.command = command;
        }
    }
    private ServerSocketChannel server;
    private Selector selector;
    private Path rootDir = Path.of(System.getProperty("user.home"));
    private String msg;

    public NioServer() throws IOException {
        server = ServerSocketChannel.open();
        selector = Selector.open();

        server.bind(new InetSocketAddress(8189));
        server.configureBlocking(false);
        server.register(selector, SelectionKey.OP_ACCEPT);
    }

    public void start() throws IOException {
        while (server.isOpen()) {
            selector.select();

            Set<SelectionKey> keys = selector.selectedKeys();
            Iterator<SelectionKey> iterator = keys.iterator();

            while (iterator.hasNext()) {
                SelectionKey key = iterator.next();
                if (key.isAcceptable()) {
                    handleAccept();
                }
                if (key.isReadable()) {
                    handleRead(key);
                }
                iterator.remove();
            }
        }
    }
    private void handleAccept() throws IOException {
        SocketChannel channel = server.accept();
        channel.configureBlocking(false);
        channel.register(selector, SelectionKey.OP_READ);
        channel.write(ByteBuffer.wrap("---Welcome in my Terminal!---\n".getBytes(StandardCharsets.UTF_8)));
        channel.write(ByteBuffer.wrap(("$" + rootDir + "$: ").getBytes(StandardCharsets.UTF_8)));
    }

    private void handleRead(SelectionKey key) throws IOException {
        ByteBuffer buffer = ByteBuffer.allocate(1024);
        SocketChannel channel = (SocketChannel) key.channel();
        StringBuilder sb = new StringBuilder();

        while (channel.isOpen()) {
            int read = channel.read(buffer);
            if (read < 0) {
                channel.close();
                return;
            }
            if (read == 0) {
                break;
            }

            buffer.flip();
            while (buffer.hasRemaining()) {
                sb.append((char) buffer.get());
            }
            buffer.clear();
        }

        msg = sb.toString().replace("\n","").replace("\r","");

        if (msg.equals(CommandType.LS.getCommand())) {
            lsHandlerStream(channel);
        }
        if (msg.startsWith(CommandType.CD.getCommand())) {
            cdHandler(channel);
        }
        if (msg.startsWith(CommandType.CAT.getCommand())) {
            catHandlerStream(channel);
        }
        if (msg.equals(CommandType.EXIT.getCommand())) {
            channel.write(ByteBuffer.wrap("---Thank you. Terminal is closed. Bye.---\n".getBytes(StandardCharsets.UTF_8)));
            channel.close();
            return;
        }
        channel.write(ByteBuffer.wrap(("$" + rootDir + "$: ").getBytes(StandardCharsets.UTF_8)));
    }

    private void lsHandlerStream(SocketChannel channel) {
        try(Stream<Path> stream = Files.list(Paths.get(String.valueOf(rootDir)))) {
            stream.forEach(file -> {
                try {
                    channel.write(ByteBuffer.wrap(file.getFileName().toString().getBytes(StandardCharsets.UTF_8)));
                    channel.write(ByteBuffer.wrap("\n".getBytes(StandardCharsets.UTF_8)));
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void cdHandler(SocketChannel channel) throws IOException {
        String[] strArray = msg.split(" ", 2);
        if (strArray[1].equals("..") || rootDir.resolve(strArray[1]).toFile().isDirectory()) {
            rootDir = rootDir.resolve(strArray[1]).normalize();
        } else {
            channel.write(ByteBuffer.wrap((strArray[1] + " is not a Directory!\n").getBytes(StandardCharsets.UTF_8)));
        }
    }

    private void catHandlerStream(SocketChannel channel) throws IOException {
        String[] strArray = msg.split(" ", 2);
        if (rootDir.resolve(strArray[1]).toFile().isFile()) {
            try (Stream<String> stream = Files.lines(Paths.get(String.valueOf(rootDir)).resolve(strArray[1]))) {
                stream.forEach(x -> {
                    try {
                        channel.write(ByteBuffer.wrap((x + "\n").getBytes(StandardCharsets.UTF_8)));
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                });
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else {
            channel.write(ByteBuffer.wrap((strArray[1] + " is not a file. Cannot read!\n").getBytes(StandardCharsets.UTF_8)));
        }
    }
}
