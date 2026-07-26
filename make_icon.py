import os
import zlib

def create_chunk(type_bytes, data):
    length = len(data).to_bytes(4, 'big')
    crc_data = type_bytes + data
    crc = zlib.crc32(crc_data) & 0xffffffff
    return length + type_bytes + data + crc.to_bytes(4, 'big')

def create_png(width, height):
    signature = b'\x89PNG\r\n\x1a\n'
    
    ihdr_data = (
        width.to_bytes(4, 'big') +
        height.to_bytes(4, 'big') +
        b'\x08' +  # bit depth
        b'\x06' +  # color type (RGBA)
        b'\x00' +  # compression
        b'\x00' +  # filter
        b'\x00'    # interlace
    )
    ihdr_chunk = create_chunk(b'IHDR', ihdr_data)
    
    raw_data = b''
    for y in range(height):
        raw_data += b'\x00'
        for x in range(width):
            raw_data += bytes([255, 100, 100, 255])
    
    compressed = zlib.compress(raw_data)
    idat_chunk = create_chunk(b'IDAT', compressed)
    iend_chunk = create_chunk(b'IEND', b'')
    
    return signature + ihdr_chunk + idat_chunk + iend_chunk

sizes = {'mdpi': 48, 'hdpi': 72, 'xhdpi': 96, 'xxhdpi': 144, 'xxxhdpi': 192}
res_path = 'app/src/main/res'

for density, size in sizes.items():
    dir_path = os.path.join(res_path, f'mipmap-{density}')
    os.makedirs(dir_path, exist_ok=True)
    png_data = create_png(size, size)
    with open(os.path.join(dir_path, 'ic_launcher.png'), 'wb') as f:
        f.write(png_data)
    print(f'Created mipmap-{density}/ic_launcher.png')

print('All icons created successfully!')