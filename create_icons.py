import os
import struct
import zlib

def create_png(width, height, r, g, b):
    png = bytearray([0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A])
    
    ihdr = struct.pack('>IIBBBBB', width, height, 8, 6, 0, 0, 0)
    crc = zlib.crc32(b'IHDR' + ihdr) & 0xffffffff
    png.extend(struct.pack('>I', 13))
    png.extend(b'IHDR')
    png.extend(ihdr)
    png.extend(struct.pack('>I', crc))
    
    radius = int(min(width, height) * 0.25)
    raw = bytearray()
    
    for y in range(height):
        raw.append(0)
        for x in range(width):
            dx = min(x, width-1-x)
            dy = min(y, height-1-y)
            
            if dx < radius and dy < radius:
                if (radius-dx)**2 + (radius-dy)**2 > radius**2:
                    raw.extend([0, 0, 0, 0])
                    continue
            
            raw.extend([r, g, b, 255])
    
    compressed = zlib.compress(raw)
    crc = zlib.crc32(b'IDAT' + compressed) & 0xffffffff
    png.extend(struct.pack('>I', len(compressed)))
    png.extend(b'IDAT')
    png.extend(compressed)
    png.extend(struct.pack('>I', crc))
    
    png.extend(b'\x00\x00\x00\x00IEND\xAE\x42\x60\x82')
    
    return bytes(png)

base = r'd:\360MoveData\Users\vvcxxc\Desktop\jmcomic-qqbot-wrapper-main\app\src\main\res'
sizes = {
    'mipmap-mdpi': 48,
    'mipmap-hdpi': 72,
    'mipmap-xhdpi': 96,
    'mipmap-xxhdpi': 144,
    'mipmap-xxxhdpi': 192
}

for folder, size in sizes.items():
    path = os.path.join(base, folder)
    os.makedirs(path, exist_ok=True)
    
    png = create_png(size, size, 52, 131, 235)
    
    with open(os.path.join(path, 'ic_launcher.png'), 'wb') as f:
        f.write(png)
    print(f'Created {folder}/ic_launcher.png')
    
    with open(os.path.join(path, 'ic_launcher_round.png'), 'wb') as f:
        f.write(png)
    print(f'Created {folder}/ic_launcher_round.png')

print('\nDone!')