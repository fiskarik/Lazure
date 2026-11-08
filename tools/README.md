# Вспомогательные инструменты

| Каталог | Назначение |
|---|---|
| [`models/`](models) | `manifest.json`: модели, источники и лицензии. `fetch_models.sh` скачивает их в APK. `export_mossformer2.py` экспортирует сепаратор в ONNX |
| [`eval/`](eval) | `evaluate.py`: WER, cpWER, DER, ошибки границ реплик и RTF по JSON в формате конкурса. Самотест: `python3 -m unittest discover -s tools/eval` |
| [`bench/`](bench) | Синтетические русские диалоги с разметкой (`gen_dialogs.py`), оценка разметки говорящих по logcat (`evaluate_speakers.py`), проверка правила границ реплик на реальной GigaAM (`token_timing_check.py`), диалоги с перебиваниями из живой речи (`make_overlap_dialogs.py`) |
