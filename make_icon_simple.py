import os
import struct
import zlib

def create_icon_folder(base_path, folder_name):
    folder_path = os.path.join(base_path, folder_name)
    if not os.path.exists(folder_path):
        os.makedirs(folder_path)
    return folder_path

def create_png(width, height, r, g, b, corner_radius_ratio=0.25):
    png = bytearray([0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A])
    
    ihdr_data = struct.pack('>IIBBBBB', width, height, 8, 6, 0, 0, 0)
    ihdr = b'IHDR' + ihdr_data
    ihdr_crc = zlib.crc32(ihdr) & 0xffffffff
    png.extend(struct.pack('>I', 13))
    png.extend(b'IHDR')
    png.extend(ihdr_data)
    png.extend(struct.pack('>I', ihdr_crc))
    
    raw_data = bytearray()
    radius = int(min(width, height) * corner_radius_ratio)
    
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
    idat_crc = zlib.crc32(b'IDAT' + compressed) & 0xffffffff
    png.extend(struct.pack('>I', len(compressed)))
    png.extend(b'IDAT')
    png.extend(compressed)
    png.extend(struct.pack('>I', idat_crc))
    
    iend_crc = zlib.crc32(b'IEND') & 0xffffffff
    png.extend(b'\x00\x00\x00\x00IEND\xAE\x42\x60\x82')
    
    return bytes(png)

base_path = r'd:\360MoveData\Users\vvcxxc\Desktop\jmcomic-qqbot-wrapper-main\app\src\main\res'

icon_configs = [
    ('mipmap-mdpi', 48),
    ('mipmap-hdpi', 72),
    ('mipmap-xhdpi', 96),
    ('mipmap-xxhdpi', 144),
    ('mipmap-xxxhdpi', 192)
]

for folder_name, size in icon_configs:
    folder_path = create_icon_folder(base_path, folder_name)
    png_data = create_png(size, size, 52, 131, 235)
    
    launcher_path = os.path.join(folder_path, 'ic_launcher.png')
    with open(launcher_path, 'wb') as f:
        f.write(png_data)
    print(f'Created: {launcher_path}')
    
    round_path = os.path.join(folder_path, 'ic_launcher_round.png')
    with open(round_path, 'wb') as f:
        f.write(png_data)
    print(f'Created: {round_path}')

print('\n✅ 图标生成完成！')