# Сборка APK через GitHub (без Android Studio)

## Шаг 1: Создайте аккаунт на GitHub
Если нет аккаунта, зарегистрируйтесь на https://github.com

## Шаг 2: Создайте новый репозиторий
1. Зайдите на https://github.com/new
2. Название: `LeapmotorTranslator`
3. Оставьте Public
4. НЕ ставьте галочку "Add README"
5. Нажмите **Create repository**

## Шаг 3: Установите Git (если нет)
Скачайте и установите Git: https://git-scm.com/download/win

## Шаг 4: Загрузите проект на GitHub
Откройте PowerShell в папке LeapmotorTranslator и выполните:

```powershell
git init
git add .
git commit -m "Initial commit"
git branch -M main
git remote add origin https://github.com/ВАШ_ЛОГИН/LeapmotorTranslator.git
git push -u origin main
```

## Шаг 5: Подождите сборку
1. Откройте ваш репозиторий на GitHub
2. Перейдите во вкладку **Actions**
3. Вы увидите запущенную сборку "Build APK"
4. Подождите 5-10 минут

## Шаг 6: Скачайте APK
1. После успешной сборки (зелёная галочка ✓)
2. Нажмите на сборку "Build APK"
3. Внизу страницы найдите **Artifacts**
4. Скачайте `LeapmotorTranslator-debug`
5. Распакуйте ZIP - там ваш APK!

---

## Альтернатива: Онлайн-сборщики

Если GitHub не подходит, можно использовать:

### AppCreator24 (бесплатно)
https://www.appcreator24.com
- Простой, но ограниченный функционал

### Kodular / MIT App Inventor
- Визуальные редакторы, не подходят для сложных проектов

---

## Для сборки на своём ПК нужно:
1. **JDK 17**: https://adoptium.net/temurin/releases/?version=17
2. **Android SDK**: Установится с Android Studio

После установки JDK выполните:
```powershell
cd "c:\Users\User\Downloads\Новая папка (3)\LeapmotorTranslator"
.\gradlew.bat assembleDebug
```
