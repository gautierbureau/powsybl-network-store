import asyncio
import sys

DELAY = float(sys.argv[1]) if len(sys.argv) > 1 else 0.003
BLOCK_COLLECTIONS = len(sys.argv) > 2 and sys.argv[2] == 'block'
LISTEN = 8081
TARGET = 8080

NOT_FOUND = (b'HTTP/1.1 404 Not Found\r\nContent-Length: 0\r\nConnection: keep-alive\r\n\r\n')


async def pump(reader, writer, delay, client_writer=None):
    try:
        while True:
            data = await reader.read(65536)
            if not data:
                break
            if client_writer is not None and data[:4] in (b'GET ', b'POST', b'PUT ', b'DELE'):
                print(data.split(b'\r\n', 1)[0].decode('latin1'), flush=True)
            if client_writer is not None and BLOCK_COLLECTIONS and (b'/collections HTTP' in data or b'/bulk-update HTTP' in data):
                # simulate an old server without the collections endpoint
                client_writer.write(NOT_FOUND)
                await client_writer.drain()
                continue
            if delay:
                await asyncio.sleep(delay)
            writer.write(data)
            await writer.drain()
    except (ConnectionResetError, BrokenPipeError):
        pass
    finally:
        try:
            writer.close()
        except Exception:
            pass


async def handle(client_reader, client_writer):
    try:
        server_reader, server_writer = await asyncio.open_connection('127.0.0.1', TARGET)
    except OSError:
        client_writer.close()
        return
    await asyncio.gather(
        pump(client_reader, server_writer, DELAY, client_writer),  # requests: add latency
        pump(server_reader, client_writer, 0),                     # responses: no extra latency
    )


async def main():
    server = await asyncio.start_server(handle, '127.0.0.1', LISTEN)
    print(f'proxy on :{LISTEN} -> :{TARGET} delay={DELAY*1000:.0f}ms', flush=True)
    async with server:
        await server.serve_forever()

asyncio.run(main())
