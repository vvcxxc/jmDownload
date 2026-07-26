import os
import zlib

def create_png(width, height, r, g, b):
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
        raw_data += b'\x00'  # filter byte
        for x in range(width):
            raw_data += bytes([r, g, b, 255])
    
    compressed = zlib.compress(raw_data)
    idat_chunk = create_chunk(b'IDAT', compressed)
    iend_chunk = create_chunk(b'IEND', b'')
    
    return signature + ihdr_chunk + idat_chunk + iend_chunk

def create_chunk(type_bytes, data):
    length = len(data).to_bytes(4, 'big')
    crc_data = type_bytes + data
    crc = zlib.crc32(crc_data) & 0xffffffff
    return length + type_bytes + data + crc.to_bytes(4, 'big')

base_path = os.path.dirname(os.path.abspath(__file__))

mipmap_dirs = [
    os.path.join(base_path, 'app', 'src', 'main', 'res', 'mipmap-mdpi'),
    os.path.join(base_path, 'app', 'src', 'main', 'res', 'mipmap-hdpi'),
    os.path.join(base_path, 'app', 'src', 'main', 'res', 'mipmap-xhdpi'),
    os.path.join(base_path, 'app', 'src', 'main', 'res', 'mipmap-xxhdpi'),
    os.path.join(base_path, 'app', 'src', 'main', 'res', 'mipmap-xxxhdpi')
]

png_data = create_png(48, 48, 255, 0, 0)

for dir_path in mipmap_dirs:
    try:
        os.makedirs(dir_path, exist_ok=True)
        file_path = os.path.join(dir_path, 'ic_launcher.png')
        with open(file_path, 'wb') as f:
            f.write(png_data)
        print(f"Created: {file_path}")
    except Exception as e:
        print(f"Failed to create {file_path}: {e}")

print("All icons created successfully!")