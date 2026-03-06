const fs = require('fs');
const path = require('path');

function processFiles(dir) {
    const list = fs.readdirSync(dir);
    list.forEach(file => {
        const filePath = path.join(dir, file);
        const stat = fs.statSync(filePath);
        if (stat && stat.isDirectory()) {
            processFiles(filePath);
        } else if (file.endsWith('.ts')) {
            if (filePath.includes('auth.interceptor.ts')) return;

            let content = fs.readFileSync(filePath, 'utf8');
            if (content.includes("'/api")) {
                let lines = content.split('\n');
                if (!content.includes('import { environment }')) {
                    let importIndex = 0;
                    for (let i = 0; i < lines.length; i++) {
                        if (lines[i].startsWith('import')) {
                            importIndex = i;
                        }
                    }
                    lines.splice(importIndex + 1, 0, "import { environment } from 'src/environments/environment';");
                }

                content = lines.join('\n');
                content = content.replace(/'\/api/g, "environment.apiUrl + '/api");
                fs.writeFileSync(filePath, content, 'utf8');
                console.log('Updated', filePath);
            }
        }
    });
}
processFiles('src/app');
