# Test Submissions (Curl Commands)

> **Note**: The API has been updated to support multiple test cases. The `input` and `expectedOutput` fields have been replaced by a `testCases` array.

Use these commands to verify the judge service. Ensure the service is running at `http://localhost:9119`.

## 1. C Language (Single Test Case)
**Source**: `main.c` (stdio)
```c
#include <stdio.h>
int main() {
    int a, b;
    scanf("%d %d", &a, &b);
    printf("%d", a + b);
    return 0;
}
```

**Curl Command**:
```bash
curl -X POST http://localhost:9119/judge \
  -H "Content-Type: application/json" \
  -d '{
    "id": "test-c-1",
    "language": "c",
    "sourceCode": "#include <stdio.h>\nint main() {\n    int a, b;\n    scanf(\"%d %d\", &a, &b);\n    printf(\"%d\", a + b);\n    return 0;\n}",
    "testCases": [
        {
            "input": "10 20",
            "expectedOutput": "30"
        }
    ],
    "timeLimit": 1000,
    "memoryLimit": 268435456
  }'
```

---

## 2. C++ Language (Multiple Test Cases)
**Source**: `main.cpp`
```cpp
#include <iostream>
using namespace std;
int main() {
    int a, b;
    if (cin >> a >> b) {
        cout << (a + b);
    }
    return 0;
}
```

**Curl Command**:
```bash
curl -X POST http://localhost:9119/judge \
  -H "Content-Type: application/json" \
  -d '{
    "id": "test-cpp-multi",
    "language": "cpp",
    "sourceCode": "#include <iostream>\nusing namespace std;\nint main() {\n    int a, b;\n    if (cin >> a >> b) {\n        cout << (a + b);\n    }\n    return 0;\n}",
    "testCases": [
        { "input": "15 25", "expectedOutput": "40" },
        { "input": "100 200", "expectedOutput": "300" }
    ],
    "timeLimit": 1000,
    "memoryLimit": 268435456
  }'
```

---

## 3. Java Language
**Source**: `Main.java`
```java
import java.util.Scanner;
public class Main {
    public static void main(String[] args) {
        Scanner sc = new Scanner(System.in);
        if (sc.hasNextInt()) {
            int a = sc.nextInt();
            int b = sc.nextInt();
            System.out.print(a + b);
        }
    }
}
```

**Curl Command**:
```bash
curl -X POST http://localhost:9119/judge \
  -H "Content-Type: application/json" \
  -d '{
    "id": "test-java-1",
    "language": "java",
    "sourceCode": "import java.util.Scanner;\npublic class Main {\n    public static void main(String[] args) {\n        Scanner sc = new Scanner(System.in);\n        if (sc.hasNextInt()) {\n            int a = sc.nextInt();\n            int b = sc.nextInt();\n            System.out.print(a + b);\n        }\n    }\n}",
    "testCases": [
        { "input": "100 200", "expectedOutput": "300" }
    ],
    "timeLimit": 2000,
    "memoryLimit": 536870912
  }'
```

---

## 4. Python Language (Multiple Test Cases)
**Source**: `main.py`
```python
import sys
try:
    line = sys.stdin.read()
    if line:
        parts = line.split()
        if len(parts) >= 2:
            a = int(parts[0])
            b = int(parts[1])
            print(a + b, end="")
except:
    pass
```

**Curl Command**:
```bash
curl -X POST http://localhost:9119/judge \
  -H "Content-Type: application/json" \
  -d '{
    "id": "test-py-multi",
    "language": "python",
    "sourceCode": "import sys\ntry:\n    line = sys.stdin.read()\n    if line:\n        parts = line.split()\n        if len(parts) >= 2:\n            a = int(parts[0])\n            b = int(parts[1])\n            print(a + b, end=\"\")\nexcept:\n    pass",
    "testCases": [
        { "input": "5 7", "expectedOutput": "12" },
        { "input": "1 1", "expectedOutput": "2" },
        { "input": "-5 5", "expectedOutput": "0" }
    ],
    "timeLimit": 1000,
    "memoryLimit": 268435456
  }'
```

---

## 5. Go Language
**Source**: `main.go`
```go
package main
import "fmt"
func main() {
    var a, b int
    fmt.Scan(&a, &b)
    fmt.Print(a + b)
}
```

**Curl Command**:
```bash
curl -X POST http://localhost:9119/judge \
  -H "Content-Type: application/json" \
  -d '{
    "id": "test-go-1",
    "language": "go",
    "sourceCode": "package main\nimport \"fmt\"\nfunc main() {\n    var a, b int\n    fmt.Scan(&a, &b)\n    fmt.Print(a + b)\n}",
    "testCases": [
        { "input": "40 2", "expectedOutput": "42" }
    ],
    "timeLimit": 1000,
    "memoryLimit": 268435456
  }'
```

---

## 6. Mixed Results (1 AC, 1 WA)
**Description**: Python script that adds numbers correctly but fails for specific input.
```python
import sys
line = sys.stdin.read()
a, b = map(int, line.split())
if a == 10:
    print(a + b + 1, end="") # Intentional error
else:
    print(a + b, end="")
```

**Curl Command**:
```bash
curl -X POST http://localhost:9119/judge \
  -H "Content-Type: application/json" \
  -d '{
    "id": "test-mixed-wa",
    "language": "python",
    "sourceCode": "import sys\nline = sys.stdin.read()\na, b = map(int, line.split())\nif a == 10:\n    print(a + b + 1, end=\"\")\nelse:\n    print(a + b, end=\"\")",
    "testCases": [
        { "input": "1 2", "expectedOutput": "3" },
        { "input": "10 20", "expectedOutput": "30" }
    ],
    "timeLimit": 1000,
    "memoryLimit": 268435456
  }'
```
**Expected Response**: Status should be `WRONG_ANSWER` (because one case failed).

---

## 7. Error Case (Time Limit Exceeded)
**Source**: Infinite loop
```python
while True:
    pass
```

**Curl Command**:
```bash
curl -X POST http://localhost:9119/judge \
  -H "Content-Type: application/json" \
  -d '{
    "id": "test-tle-1",
    "language": "python",
    "sourceCode": "while True:\n    pass",
    "testCases": [
        { "input": "", "expectedOutput": "" }
    ],
    "timeLimit": 1000,
    "memoryLimit": 268435456
  }'
```


---

## 8. Large Scale Test (100 Test Cases)
**Description**: Python script summing two numbers, tested against 100 cases.

**Curl Command**:
```bash
curl -X POST http://localhost:9119/judge \
  -H "Content-Type: application/json" \
  -d '{
  "id": "test-c-100cases",
  "language": "c",
  "sourceCode": "#include <stdio.h>\nint main() {\n    int a, b;\n    scanf(\"%d %d\", &a, &b);\n    printf(\"%d\", a + b);\n    return 0;\n}",
  "testCases": [
    {"input": "10 20", "expectedOutput": "30"},
    {"input": "1 2", "expectedOutput": "3"},
    {"input": "3 4", "expectedOutput": "7"},
    {"input": "5 6", "expectedOutput": "11"},
    {"input": "7 8", "expectedOutput": "15"},
    {"input": "9 10", "expectedOutput": "19"},
    {"input": "11 12", "expectedOutput": "23"},
    {"input": "13 14", "expectedOutput": "27"},
    {"input": "15 16", "expectedOutput": "31"},
    {"input": "17 18", "expectedOutput": "35"},
    {"input": "19 20", "expectedOutput": "39"},
    {"input": "21 22", "expectedOutput": "43"},
    {"input": "23 24", "expectedOutput": "47"},
    {"input": "25 26", "expectedOutput": "51"},
    {"input": "27 28", "expectedOutput": "55"},
    {"input": "29 30", "expectedOutput": "59"},
    {"input": "31 32", "expectedOutput": "63"},
    {"input": "33 34", "expectedOutput": "67"},
    {"input": "35 36", "expectedOutput": "71"},
    {"input": "37 38", "expectedOutput": "75"},
    {"input": "39 40", "expectedOutput": "79"},
    {"input": "41 42", "expectedOutput": "83"},
    {"input": "43 44", "expectedOutput": "87"},
    {"input": "45 46", "expectedOutput": "91"},
    {"input": "47 48", "expectedOutput": "95"},
    {"input": "49 50", "expectedOutput": "99"},
    {"input": "51 52", "expectedOutput": "103"},
    {"input": "53 54", "expectedOutput": "107"},
    {"input": "55 56", "expectedOutput": "111"},
    {"input": "57 58", "expectedOutput": "115"},
    {"input": "59 60", "expectedOutput": "119"},
    {"input": "61 62", "expectedOutput": "123"},
    {"input": "63 64", "expectedOutput": "127"},
    {"input": "65 66", "expectedOutput": "131"},
    {"input": "67 68", "expectedOutput": "135"},
    {"input": "69 70", "expectedOutput": "139"},
    {"input": "71 72", "expectedOutput": "143"},
    {"input": "73 74", "expectedOutput": "147"},
    {"input": "75 76", "expectedOutput": "151"},
    {"input": "77 78", "expectedOutput": "155"},
    {"input": "79 80", "expectedOutput": "159"},
    {"input": "81 82", "expectedOutput": "163"},
    {"input": "83 84", "expectedOutput": "167"},
    {"input": "85 86", "expectedOutput": "171"},
    {"input": "87 88", "expectedOutput": "175"},
    {"input": "89 90", "expectedOutput": "179"},
    {"input": "91 92", "expectedOutput": "183"},
    {"input": "93 94", "expectedOutput": "187"},
    {"input": "95 96", "expectedOutput": "191"},
    {"input": "97 98", "expectedOutput": "195"},
    {"input": "99 100", "expectedOutput": "199"},
    {"input": "101 102", "expectedOutput": "203"},
    {"input": "103 104", "expectedOutput": "207"},
    {"input": "105 106", "expectedOutput": "211"},
    {"input": "107 108", "expectedOutput": "215"},
    {"input": "109 110", "expectedOutput": "219"},
    {"input": "111 112", "expectedOutput": "223"},
    {"input": "113 114", "expectedOutput": "227"},
    {"input": "115 116", "expectedOutput": "231"},
    {"input": "117 118", "expectedOutput": "235"},
    {"input": "119 120", "expectedOutput": "239"},
    {"input": "121 122", "expectedOutput": "243"},
    {"input": "123 124", "expectedOutput": "247"},
    {"input": "125 126", "expectedOutput": "251"},
    {"input": "127 128", "expectedOutput": "255"},
    {"input": "129 130", "expectedOutput": "259"},
    {"input": "131 132", "expectedOutput": "263"},
    {"input": "133 134", "expectedOutput": "267"},
    {"input": "135 136", "expectedOutput": "271"},
    {"input": "137 138", "expectedOutput": "275"},
    {"input": "139 140", "expectedOutput": "279"},
    {"input": "141 142", "expectedOutput": "283"},
    {"input": "143 144", "expectedOutput": "287"},
    {"input": "145 146", "expectedOutput": "291"},
    {"input": "147 148", "expectedOutput": "295"},
    {"input": "149 150", "expectedOutput": "299"},
    {"input": "151 152", "expectedOutput": "303"},
    {"input": "153 154", "expectedOutput": "307"},
    {"input": "155 156", "expectedOutput": "311"},
    {"input": "157 158", "expectedOutput": "315"},
    {"input": "159 160", "expectedOutput": "319"},
    {"input": "161 162", "expectedOutput": "323"},
    {"input": "163 164", "expectedOutput": "327"},
    {"input": "165 166", "expectedOutput": "331"},
    {"input": "167 168", "expectedOutput": "335"},
    {"input": "169 170", "expectedOutput": "339"},
    {"input": "171 172", "expectedOutput": "343"},
    {"input": "173 174", "expectedOutput": "347"},
    {"input": "175 176", "expectedOutput": "351"},
    {"input": "177 178", "expectedOutput": "355"},
    {"input": "179 180", "expectedOutput": "359"},
    {"input": "181 182", "expectedOutput": "363"},
    {"input": "183 184", "expectedOutput": "367"},
    {"input": "185 186", "expectedOutput": "371"},
    {"input": "187 188", "expectedOutput": "375"},
    {"input": "189 190", "expectedOutput": "379"},
    {"input": "191 192", "expectedOutput": "383"},
    {"input": "193 194", "expectedOutput": "387"},
    {"input": "195 196", "expectedOutput": "391"},
    {"input": "197 198", "expectedOutput": "395"},
    {"input": "199 200", "expectedOutput": "399"},
    {"input": "201 202", "expectedOutput": "403"},
    {"input": "203 204", "expectedOutput": "407"},
    {"input": "205 206", "expectedOutput": "411"},
    {"input": "207 208", "expectedOutput": "415"},
    {"input": "209 210", "expectedOutput": "419"},
    {"input": "211 212", "expectedOutput": "423"},
    {"input": "213 214", "expectedOutput": "427"},
    {"input": "215 216", "expectedOutput": "431"},
    {"input": "217 218", "expectedOutput": "435"},
    {"input": "219 220", "expectedOutput": "439"},
    {"input": "221 222", "expectedOutput": "443"},
    {"input": "223 224", "expectedOutput": "447"},
    {"input": "225 226", "expectedOutput": "451"},
    {"input": "227 228", "expectedOutput": "455"},
    {"input": "229 230", "expectedOutput": "459"},
    {"input": "231 232", "expectedOutput": "463"},
    {"input": "233 234", "expectedOutput": "467"},
    {"input": "235 236", "expectedOutput": "471"},
    {"input": "237 238", "expectedOutput": "475"},
    {"input": "239 240", "expectedOutput": "479"},
    {"input": "241 242", "expectedOutput": "483"},
    {"input": "243 244", "expectedOutput": "487"},
    {"input": "245 246", "expectedOutput": "491"},
    {"input": "247 248", "expectedOutput": "495"},
    {"input": "249 250", "expectedOutput": "499"}
  ],
  "timeLimit": 1000,
  "memoryLimit": 268435456
}'
```

