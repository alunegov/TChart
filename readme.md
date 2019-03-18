# TChart

## TODO

### high

- [ ] оси
  - [ ] локализация форматы даты
- [ ] вид (диалог?) со значениями сигналов на курсоре
- [x] список сигналов
- [x] вкл/выкл сигнала на графике
- [ ] анимация
  - [ ] изменения масштаба по X, Y
  - [ ] вкл/выкл сигнала
  - [ ] вкл/выкл курсора
- [ ] ускорение при перемещении или изменении размеров "зоны выделения" (как при скроллинге)
- [ ] смена темы light/dark
- [x] размеры, отступы и т.п. в формате dp (не px)
- [ ] орисовка пустого графика (все сигналы выключены)
- [ ] разделитель между именами сигналов
- [ ] более точное выставление курсора (выбирать точку сигнала, ближайщую к точке касания)
- [ ] release build
  - [ ] singing keys
  - [ ] remove tinydancer

### bugs

- [ ] при повороте экрана все имена линий становятся "#3" (имя последней линии последнего графика)

### low

- [ ] увеличить зону TOUCH_SLOPE2, когда граница находится у краёв экрана
- [ ] какой yMin использовать на графике? (сейчас всегда 0)
- [ ] немного добавлять к yMax? (часть размаха)
- [ ] размер checkbox в списке имён сигналов
  - https://stackoverflow.com/questions/2151241/android-how-to-change-checkbox-size
- [x] первым делом проверять на попадание в зону MoveMode.LEFT_AND_RIGHT
- [ ] performClick? ripple при тапе?
- [ ] rtl языки
- [x] не блокировать скроллинг у родителя (requestDisallowInterceptTouchEvent), если начальное перемещение было по вертикали
- [ ] асинхронная загрузка данных из chart_data.json
- [x] использовать ListView для вывода нескольких графиков
  - при переиспользовании вида нужно восстанавливать выделенную область, курсоры и т.п. - won't fix.
- [x] задавать элементы (и их атрибуты) TelegramChart через разметку (<merge...>)
- [ ] атрибуты TelegramChart через разметку:
  - название графика
  - размеры графика (его элементов)
  - отступы
  - цвета (фона, линий осей, надписей осей, линии курсора (=линий осей?))
  - начальное положение для PreviewChart
  - количество линий оцифровки по X и по Y
  - толщина линий (сигналов на основном и превью, оцифровки, курсора)
- [ ] MVC? чтобы "выживать" при смене ориентации экрана
- [ ] комментарии на eng
