import os

print("Testing file system access...")

# Test 1: Create a directory
test_dir = "test_keystore_dir"
try:
    if not os.path.exists(test_dir):
        os.makedirs(test_dir)
        print(f"SUCCESS: Created directory {test_dir}")
    else:
        print(f"Directory {test_dir} already exists")
except Exception as e:
    print(f"ERROR creating directory: {e}")

# Test 2: Write a file
test_file = os.path.join(test_dir, "test.txt")
try:
    with open(test_file, "w") as f:
        f.write("Hello World")
    print(f"SUCCESS: Wrote file {test_file}")
except Exception as e:
    print(f"ERROR writing file: {e}")

# Test 3: Verify file exists
try:
    if os.path.exists(test_file):
        print(f"SUCCESS: File exists: {test_file}")
        with open(test_file, "r") as f:
            content = f.read()
            print(f"File content: {content}")
    else:
        print(f"ERROR: File does not exist")
except Exception as e:
    print(f"ERROR checking file: {e}")

print("Test completed")