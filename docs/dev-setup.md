# CombatSystem 开发环境与启动说明

## 1. 先决条件
- JDK: 17（已验证路径示例：`C:\Program Files\Microsoft\jdk-17.0.8.101-hotspot`）
- Node.js: 18+（建议 20+）
- npm: 9+
- MongoDB: 本地可用（默认 `mongodb://localhost:27017/bigdb`）

## 2. Java 环境检查（Windows PowerShell）
```powershell
echo $env:JAVA_HOME
where.exe java
java -version
```

期望：
- `JAVA_HOME` 指向 JDK17 根目录
- `where.exe java` 第一条是 `%JAVA_HOME%\bin\java.exe`
- `java -version` 为 `17.x`

## 3. 前端构建
```powershell
cd C:\Users\HP\IdeaProjects\CombatSystem\frontend
npm install
npm run build
```

成功后会输出到：
- `src/main/resources/static/app/index.html`
- `src/main/resources/static/app/assets/index-*.js`

## 4. 后端启动
```powershell
cd C:\Users\HP\IdeaProjects\CombatSystem
.\mvnw.cmd spring-boot:run
```

启动成功标志：
- 日志出现 `Tomcat started on port 8080`

## 5. 页面入口
- 主入口（唯一工作台）：`http://localhost:8080/app/commander-next`
- 推演看板：`http://localhost:8080/app/simulation-dashboard`
- 运行回放：`http://localhost:8080/app/run-center`
- 战役中心：`http://localhost:8080/app/campaign-center`

## 6. 常见问题
- `java/lang/NoClassDefFoundError: java/lang/Object`
  - 说明 Java 路径被旧版本抢占，优先修复系统 `Path`（移除 Oracle javapath）
- 前端路由跳回旧页面
  - 先执行 `npm run build`，确认 `static/app/index.html` 引用的是最新 `index-*.js`
- `tailwindcss` PostCSS 报错
  - 当前项目已使用 `@tailwindcss/postcss`，不要改回旧插件名
