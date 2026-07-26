#!/usr/bin/env python3
import os
import struct
import zlib

def create_rounded_png(width, height, r, g, b):
    png = bytearray([0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A])
    
    ihdr = struct.pack('>IIBBBBB', width, height, 8, 6, 0, 0, 0)
    crc = zlib.crc32(b'IHDR' + ihdr) & 0xffffffff
    png.extend(struct.pack('>I', 13))
    png.extend(b'IHDR')
    png.extend(ihdr)
    png.extend(struct.pack('>I', crc))
    
    radius = int(min(width, height) * 0.25)
    raw_data = bytearray()
    
    for y in range(height):
        raw_data.append(0)
        for x in range(width):
            dx = min(x, width - 1 - x)
            dy = min(y, height - 1 - y)
            
            if dx < radius and dy < radius:
                dist_sq = (radius - dx) ** 2 + (radius - dy) ** 2
                if dist_sq > radius * radius:
                    raw_data.extend([0, 0, 0, 0])
                    continue
            
            raw_data.extend([r, g, b, 255])
    
    compressed = zlib.compress(raw_data)
    crc = zlib.crc32(b'IDAT' + compressed) & 0xffffffff
    png.extend(struct.pack('>I', len(compressed)))
    png.extend(b'IDAT')
    png.extend(compressed)
    png.extend(struct.pack('>I', crc))
    
    png.extend(b'\x00\x00\x00\x00IEND\xAE\x42\x60\x82')
    
    return bytes(png)

def main():
    base_path = os.path.join(os.path.dirname(__file__), 'app', 'src', 'main', 'res')
    
    icon_sizes = [
        ('mipmap-mdpi', 48),
        ('mipmap-hdpi', 72),
        ('mipmap-xhdpi', 96),
        ('mipmap-xxhdpi', 144),
        ('mipmap-xxxhdpi', 192)
    ]
    
    r, g, b = 52, 131, 235
    
    for folder_name, size in icon_sizes:
        folder_path = os.path.join(base_path, folder_name)
        os.makedirs(folder_path, exist_ok=True)
        
        png_data = create_rounded_png(size, size, r, g, b)
        
        launcher_path = os.path.join(folder_path, 'ic_launcher.png')
        with open(launcher_path, 'wb') as f:
            f.write(png_data)
        print(f'Created: {launcher_path}')
        
        round_path = os.path.join(folder_path, 'ic_launcher_round.png')
        with open(round_path, 'wb') as f:
            f.write(png_data)
        print(f'Created: {round_path}')
    
    print('\n✅ 图标生成完成！')

if __name__ == '__main__':
    main()
    input('按任意键退出...')