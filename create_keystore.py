import os
import subprocess
import sys

def main():
    keystore_dir = "keystore"
    keystore_path = os.path.join(keystore_dir, "release.jks")
    
    # Create keystore directory
    if not os.path.exists(keystore_dir):
        os.makedirs(keystore_dir)
        print(f"Created directory: {keystore_dir}")
    else:
        print(f"Directory {keystore_dir} already exists")
    
    # Check if keystore already exists
    if os.path.exists(keystore_path):
        print(f"Keystore already exists: {keystore_path}")
        return
    
    # Find Java home from environment or common locations
    java_home = os.environ.get("JAVA_HOME")
    if not java_home:
        java_home = "C:\\Android\\jdk-17.0.19+10"
    
    keytool_path = os.path.join(java_home, "bin", "keytool.exe")
    
    if not os.path.exists(keytool_path):
        print(f"keytool not found at: {keytool_path}")
        return
    
    # Generate keystore
    command = [
        keytool_path,
        "-genkey", "-v",
        "-keystore", keystore_path,
        "-keyalg", "RSA",
        "-keysize", "2048",
        "-validity", "10000",
        "-alias", "release",
        "-storepass", "android",
        "-keypass", "android",
        "-dname", "CN=JM Download, OU=JM, O=JM, L=Unknown, ST=Unknown, C=CN",
        "-noprompt"
    ]
    
    print(f"Running command: {' '.join(command)}")
    
    try:
        result = subprocess.run(command, capture_output=True, text=True)
        if result.returncode == 0:
            print(f"Keystore created successfully: {keystore_path}")
        else:
            print(f"Error creating keystore: {result.stderr}")
            sys.exit(1)
    except Exception as e:
        print(f"Exception: {e}")
        sys.exit(1)

if __name__ == "__main__":
    main()