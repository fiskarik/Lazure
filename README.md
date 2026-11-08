# Lazure — нейро-стенографист для Android

Решение команды для кейса **«Нейро-стенографист»** (Nuclear IT Hack, КСиТ × HEX.TEAM).

Lazure локально, **без интернета**, превращает аудиозапись разговора в структурированную расшифровку:

- кто говорит;
- что сказано;
- когда реплика началась и закончилась.

Результат можно посмотреть в приложении с плеером и экспортировать в JSON по схеме конкурса. Краткое содержание разговора и ответы на вопросы по нему даёт локальная LLM.

| Требование задания | Как выполнено | Где в коде |
|---|---|---|
| Работает под Android | Kotlin + Jetpack Compose, Android 7.0+ (minSdk 24) | `src/mobile/android` |
| Использует локальную LLM | Qwen3 0.6B INT4 через LiteRT-LM, встроена в APK: краткое содержание и вопросы по записи | `data/recording/RecordingAi.kt`, `data/chat/ChatEngine.kt` |
| Импортирует аудио из памяти устройства | Системный выбор файла (SAF), любой формат, который декодирует Android | `data/recording/RecordingRepository.kt` |
| Записывает аудио из интерфейса | Foreground-сервис, WAV 16 кГц, системный AEC | `service/RecordingService.kt` |
| Преобразует речь в текст | GigaAM v3 CTC с пунктуацией (sherpa-onnx) | `data/recording/SpeechPipeline.kt` |
| Определяет и разделяет говорящих | pyannote 3.0 + CAM++, уточнение по эмбеддингам предложений с учётом подсказок диаризатора (кто говорил одновременно) | `SpeechPipeline.refineSpeakers`, `DiarizationEvidence` |
| Временные метки реплик | Время токенов GigaAM CTC: начало первого и метка последнего токена + 80 мс (правило выбрано замером) | `SpeechPipeline.groupTokens`, `tools/bench/token_timing_check.py` |
| Отображает расшифровку | Плеер с волной, подсветка текущей реплики, переход по клику | `ui/recording/` |
| Экспортирует в файл | JSON `{"transcript":[{speaker_id,start_ms,end_ms,text}]}` | `data/recording/TranscriptExport.kt` |
| Всё на устройстве | Облачных API нет; сеть нужна только для необязательной загрузки других LLM | — |

Дополнительно реализовано:

- **Средний уровень:**
  - шумоподавление (DTLN), ФВЧ, нормализация;
  - фильтрация пауз (Silero VAD);
  - устойчивое определение говорящих на смене реплик;
  - пунктуация;
  - прогресс обработки;
  - воспроизведение с переходом к реплике.
- **Ultra Pro Max:**
  - разделение одновременной речи (MossFormer2) и пометка перебиваний;
  - короткие реплики («Угу», «Да») получают своего говорящего;
  - экономия памяти (блочная обработка, выгрузка ASR на время разделения);
  - правка текста и имён говорящих, слияние говорящих, повторная обработка;
  - время обработки и RTF на карточке записи.

## Быстрый старт для проверки

1. Установите APK со страницы **Releases** репозитория. Модели речи и LLM уже внутри APK, интернет не нужен.
2. Откройте приложение. При первом запуске встроенная LLM копируется во внутреннее хранилище (несколько секунд).
3. **Главная → «Импорт»** → выберите аудиофайл. Обработка начнётся сама, прогресс виден в карточке записи.
4. Когда статус станет «Расшифровка готова», в карточке появятся:
   - реплики с говорящими и временем;
   - строка «Обработано за X с · RTF Y»;
   - кнопки «Суммаризация» и «Спросить ИИ».
5. **«Экспорт JSON»** → выберите место сохранения → файл в формате конкурса.

Если известно число говорящих, его можно задать заранее: **Настройки → Записи → Число говорящих** (по умолчанию «Авто»). Разделение наложений и шумоподавление включаются и выключаются на экране **Главная → Модели ИИ**.

## Сборка из исходников

Требования: JDK 21 (Gradle сам подберёт toolchain), Android SDK 36, NDK `27.1.12297006`, CMake `3.22.1`.

```bash
bash tools/models/fetch_models.sh
```

Скрипт скачивает модели в `src/mobile/android/src/main/assets/models`, JNI-библиотеку sherpa-onnx в `jniLibs` и встроенную LLM в `assets/bundled_llm`. Модель разделения MossFormer2 необязательна; как её собрать, описано в `tools/models/export_mossformer2.py`.

```bash
./gradlew :app:assembleRelease -Plazure.abis=arm64-v8a
```

Это release-APK для телефона: R8, только arm64. Без своего ключа он подписывается debug-ключом. Свой ключ задаётся в `local.properties` полями `lazure.storeFile`, `lazure.storePassword`, `lazure.keyAlias`, `lazure.keyPassword`. Результат: `src/mobile/android/build/outputs/apk/release/app-release.apk`.

```bash
./gradlew :app:assembleDebug
```

Debug-сборка для эмулятора (arm64 + x86_64). `./run-android.sh [AVD]` запускает эмулятор, ставит и открывает приложение.

Для замеров скорости используйте release-сборку: в debuggable-приложении Kotlin-код (DSP, кластеризация) работает заметно медленнее.

## Тесты и оценка качества

```bash
./gradlew :app:testDebugUnitTest
```

Unit-тесты: экспорт JSON, временные метки, статусы обработки, AI по записи, PIN и др.

```bash
./gradlew :app:connectedDebugAndroidTest
```

Инструментальные тесты: миграции БД, экраны, пайплайн на устройстве.

```bash
python3 tools/eval/evaluate.py --ref refs/ --hyp exports/ --timing timing.csv --csv artifacts/test-results/results.csv
```

Скрипт оценки считает WER, cpWER, DER, ошибки границ реплик и RTF.

`SpeechPipelineJvmTest` прогоняет настоящий пайплайн приложения на компьютере, на Linux-сборке sherpa-onnx, и пишет JSON для этого скрипта. Так изменения распознавания и диаризации проверяются на реальных записях без телефона.

Методика и результаты: [docs/TEST_RESULTS.md](docs/TEST_RESULTS.md).

## Документация

| Документ | Содержание |
|---|---|
| [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) | Архитектура, пайплайн, модели и лицензии, приватность |
| [docs/PROTOCOL.md](docs/PROTOCOL.md) | Формат входа и JSON-вывода, правила формирования реплик и меток |
| [docs/LIMITATIONS.md](docs/LIMITATIONS.md) | Ограничения, риски демонстрации, планы |
| [docs/TEST_RESULTS.md](docs/TEST_RESULTS.md) | Методика тестирования и результаты |
| [docs/DEVELOPMENT_LOG.md](docs/DEVELOPMENT_LOG.md) | Журнал разработки: решения и найденные причины ошибок |
| [artifacts/demo](artifacts/demo) | Демонстрация работы (видео) |
| [artifacts/test-results](artifacts/test-results) | Результаты прогонов скрипта оценки |

## Структура репозитория

```
src/mobile/android/     Android-приложение (модуль :app)
  src/main/java/com/rassvet/essential/
    data/recording/     пайплайн распознавания, очередь, экспорт, AI по записи
    data/llm/           локальная LLM (LiteRT-LM), каталог и загрузка моделей
    data/local/         Room и настройки
    ui/                 экраны (Compose)
  src/main/java/com/k2fsa/sherpa/onnx/   Kotlin-API sherpa-onnx
tools/models/           манифест и загрузка моделей, экспорт MossFormer2
tools/eval/             оценка WER / cpWER / DER / таймстемпов / RTF
tools/bench/            синтетический бенчмарк диаризации
docs/                   документация по шаблону хакатона
artifacts/              демо и результаты тестов
config/                 пример переменных для скриптов
```

## Стек

Kotlin, Jetpack Compose, Material 3, Hilt, Room, DataStore; sherpa-onnx 2.0.1 (ONNX Runtime), onnxruntime-android 1.28, LiteRT-LM 0.12. Модели: Silero VAD, GigaAM v3 CTC, pyannote segmentation 3.0, 3D-Speaker CAM++, DTLN, MossFormer2, Qwen3 0.6B.
