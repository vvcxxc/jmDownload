import os
import struct
import zlib

def create_png_chunk(type_bytes, data):
    length = struct.pack('>I', len(data))
    crc_data = type_bytes + data
    crc = zlib.crc32(crc_data) & 0xffffffff
    crc_bytes = struct.pack('>I', crc)
    return length + type_bytes + data + crc_bytes

def create_rounded_png(width, height, r, g, b, a=255, corner_radius=0.25):
    png_data = b'\x89PNG\r\n\x1a\n'
    ihdr = struct.pack('>IIBBBBB', width, height, 8, 6, 0, 0, 0)
    png_data += create_png_chunk(b'IHDR', ihdr)
    
    raw_data = bytearray()
    radius = int(min(width, height) * corner_radius)
    
    for y in range(height):
        raw_data.append(0)
        for x in range(width):
            dist_x = min(x, width - 1 - x)
            dist_y = min(y, height - 1 - y)
            
            if dist_x < radius and dist_y < radius:
                dx = radius - dist_x
                dy = radius - dist_y
                if dx * dx + dy * dy > radius * radius:
                    raw_data.extend([0, 0, 0, 0])
                    continue
            
            raw_data.append(r)
            raw_data.append(g)
            raw_data.append(b)
            raw_data.append(a)
    
    compressed = zlib.compress(raw_data)
    png_data += create_png_chunk(b'IDAT', compressed)
    png_data += create_png_chunk(b'IEND', b'')
    
    return png_data

def main():
    base_path = r'd:\360MoveData\Users\vvcxxc\Desktop\jmcomic-qqbot-wrapper-main\app\src\main\res'
    
    icon_sizes = {
        'mipmap-mdpi': 48,
        'mipmap-hdpi': 72,
        'mipmap-xhdpi': 96,
        'mipmap-xxhdpi': 144,
        'mipmap-xxxhdpi': 192
    }
    
    r, g, b = 52, 131, 235
    
    for folder, size in icon_sizes.items():
        folder_path = os.path.join(base_path, folder)
        os.makedirs(folder_path, exist_ok=True)
        print(f"Created folder: {folder_path}")
        
        png_data = create_rounded_png(size, size, r, g, b)
        
        file_path = os.path.join(folder_path, 'ic_launcher.png')
        with open(file_path, 'wb') as f:
            f.write(png_data)
        print(f"Generated: {file_path}")
        
        file_path_round = os.path.join(folder_path, 'ic_launcher_round.png')
        with open(file_path_round, 'wb') as f:
            f.write(png_data)
        print(f"Generated: {file_path_round}")
    
    print("\n✅ 图标生成完成！")

if __name__ == '__main__':
    main()