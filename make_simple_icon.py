#!/usr/bin/env python3
import os
import zlib
import struct

def crc32(data):
    return zlib.crc32(data) & 0xffffffff

def create_png_chunk(chunk_type, data):
    length = struct.pack('>I', len(data))
    chunk_data = chunk_type + data
    crc = struct.pack('>I', crc32(chunk_data))
    return length + chunk_data + crc

def create_png(width, height, r, g, b, a=255):
    png_data = b'\x89PNG\r\n\x1a\n'
    
    ihdr = struct.pack('>IIBBBBB', width, height, 8, 6, 0, 0, 0)
    png_data += create_png_chunk(b'IHDR', ihdr)
    
    raw_data = bytearray()
    for y in range(height):
        raw_data.append(0)
        for x in range(width):
            raw_data.append(r)
            raw_data.append(g)
            raw_data.append(b)
            raw_data.append(a)
    
    compressed = zlib.compress(raw_data)
    png_data += create_png_chunk(b'IDAT', compressed)
    
    png_data += create_png_chunk(b'IEND', b'')
    
    return png_data

def main():
    sizes = {
        'mipmap-mdpi': 48,
        'mipmap-hdpi': 72,
        'mipmap-xhdpi': 96,
        'mipmap-xxhdpi': 144,
        'mipmap-xxxhdpi': 192
    }
    
    base_dir = 'app/src/main/res'
    
    print('Creating directories...')
    for dir_name in sizes.keys():
        dir_path = os.path.join(base_dir, dir_name)
        os.makedirs(dir_path, exist_ok=True)
        print(f'  Created: {dir_path}')
    
    print('\nCreating icons...')
    for dir_name, size in sizes.items():
        png = create_png(size, size, 79, 129, 189, 255)
        output_path = os.path.join(base_dir, dir_name, 'ic_launcher.png')
        with open(output_path, 'wb') as f:
            f.write(png)
        print(f'  Created: {output_path} ({size}x{size})')
    
    print('\nDone!')

if __name__ == '__main__':
    main()