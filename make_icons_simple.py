import os
import struct
import zlib

def create_png(w, h, r, g, b):
    png = bytearray([0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A])
    
    ihdr = struct.pack('>IIBBBBB', w, h, 8, 6, 0, 0, 0)
    crc = zlib.crc32(b'IHDR' + ihdr) & 0xffffffff
    png.extend(struct.pack('>I', 13))
    png.extend(b'IHDR')
    png.extend(ihdr)
    png.extend(struct.pack('>I', crc))
    
    rad = int(min(w, h) * 0.25)
    raw = bytearray()
    for y in range(h):
        raw.append(0)
        for x in range(w):
            dx = min(x, w-1-x)
            dy = min(y, h-1-y)
            if dx < rad and dy < rad:
                if (rad-dx)**2 + (rad-dy)**2 > rad**2:
                    raw.extend([0,0,0,0])
                    continue
            raw.extend([r, g, b, 255])
    
    comp = zlib.compress(raw)
    crc = zlib.crc32(b'IDAT' + comp) & 0xffffffff
    png.extend(struct.pack('>I', len(comp)))
    png.extend(b'IDAT')
    png.extend(comp)
    png.extend(struct.pack('>I', crc))
    
    png.extend(b'\x00\x00\x00\x00IEND\xAE\x42\x60\x82')
    return bytes(png)

base = r'D:\360MoveData\Users\vvcxxc\Desktop\jmcomic-qqbot-wrapper-main\app\src\main\res'
for name, size in [('mipmap-mdpi', 48), ('mipmap-hdpi', 72), ('mipmap-xhdpi', 96), ('mipmap-xxhdpi', 144), ('mipmap-xxxhdpi', 192)]:
    path = os.path.join(base, name)
    if not os.path.exists(path):
        os.mkdir(path)
    png = create_png(size, size, 52, 131, 235)
    with open(os.path.join(path, 'ic_launcher.png'), 'wb') as f:
        f.write(png)
    with open(os.path.join(path, 'ic_launcher_round.png'), 'wb') as f:
        f.write(png)
    print(f'Created {name}')
print('Done!')