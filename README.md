# Leapmotor C11 Translation Overlay

Высокопроизводительное Android-приложение для перевода китайского интерфейса автомобиля Leapmotor C11 (2022) на русский язык в реальном времени.

## Возможности

- 🚗 **Оптимизировано для Leapmotor C11**: Snapdragon 8155, Adreno 640, Android 9 Automotive
- 🈯 **Офлайн-перевод**: Google ML Kit для перевода без интернета (после загрузки модели)
- 🎨 **Гибридный оверлей**: OpenGL ES 3.0 шейдер + Canvas рендеринг
- ⚡ **Предсказание движения**: Фильтр Калмана для компенсации лага при скролле
- 🖐️ **Прозрачность для касаний**: Оригинальные кнопки остаются кликабельными

## Архитектура

```
┌─────────────────────────────────────────────────────┐
│                 Input Layer                         │
│  AccessibilityService → UI Tree → Text Extraction   │
└────────────────────────┬────────────────────────────┘
                         │
┌────────────────────────▼────────────────────────────┐
│               Processing Layer                       │
│  ML Kit Translation ← Cache ← Kalman Prediction     │
└────────────────────────┬────────────────────────────┘
                         │
┌────────────────────────▼────────────────────────────┐
│                Visual Layer                          │
│  Layer 1: OpenGL Eraser Shader (masks Chinese)      │
│  Layer 2: Canvas Text Overlay (draws Russian)       │
└─────────────────────────────────────────────────────┘
```

## Требования

- Android Studio Arctic Fox или новее
- JDK 17+
- Android SDK 28 (Android 9 Pie)
- Устройство с OpenGL ES 3.0

## Сборка APK

### Вариант 1: Android Studio
1. Откройте папку `LeapmotorTranslator` в Android Studio
2. Дождитесь синхронизации Gradle
3. Build → Build Bundle(s) / APK(s) → Build APK(s)
4. APK будет в `app/build/outputs/apk/debug/`

### Вариант 2: Командная строка
```batch
cd LeapmotorTranslator
gradlew.bat assembleDebug
```

## Установка

1. Скопируйте APK на устройство
2. Установите APK
3. Откройте приложение "Leapmotor Translator"
4. Нажмите "Разрешение на наложение" и включите
5. Нажмите "Настройки доступности" и включите "Переводчик интерфейса"
6. Вернитесь в приложение - перевод должен работать

## Структура проекта

```
LeapmotorTranslator/
├── app/src/main/
│   ├── java/com/leapmotor/translator/
│   │   ├── TranslationService.kt     # Главный AccessibilityService
│   │   ├── MainActivity.kt           # UI настроек
│   │   ├── BootReceiver.kt          # Автозапуск
│   │   ├── filter/
│   │   │   └── KalmanFilter.kt      # Предсказание координат
│   │   ├── renderer/
│   │   │   ├── OverlayRenderer.kt   # OpenGL рендерер
│   │   │   └── TextOverlay.kt       # Canvas текст
│   │   └── translation/
│   │       └── TranslationManager.kt # ML Kit + кэш
│   └── res/
│       └── raw/
│           ├── eraser_vertex.glsl   # Вершинный шейдер
│           └── eraser_fragment.glsl # Фрагментный шейдер
└── build.gradle.kts
```

## Оптимизации

- **Кэширование переводов**: HashMap с 5000 записей
- **Пул фильтров Калмана**: Минимизация аллокаций
- **Debouncing**: 50мс задержка между обновлениями
- **highp float в шейдерах**: Оптимально для Adreno 640
- **Hardware Acceleration**: Включено для всех View

## Известные ограничения

- Требует ручного включения AccessibilityService
- Первый запуск загружает модель перевода (~50MB)
- Работает только на центральном экране 1920x1080

## Лицензия

MIT License
