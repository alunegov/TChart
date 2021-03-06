# TODO v1

## high

- [x] оси
  - [x] красивые числа по Y
  - [x] начало отсчёта
  - [x] локализация формата даты
    - используем "MMM dd", порядок дня/месяца берём из android.text.format.DateFormat.getDateFormatOrder
  - [x] текcт X по центру точки
- [x] вид (~~диалог?~~ попап) со значениями сигналов на курсоре
- [x] список сигналов
- [x] вкл/выкл сигнала на графике
- [x] анимация
  - [x] изменения масштаба по X, Y
  - [x] вкл/выкл сигнала
  - [x] вкл/выкл курсора
    - won't fix
- [ ] ускорение при перемещении или изменении размеров "зоны выделения" (как при скроллинге)
- [x] смена темы light/dark
- [x] размеры, отступы и т.п. в формате dp (не px)
- [x] отрисовка пустого графика (все сигналы выключены)
- [x] разделитель между именами сигналов
- [x] более точное выставление курсора (выбирать точку сигнала, ближайщую к точке касания)
- [x] release build
  - [x] singing keys
  - [x] remove tinydancer
    - в release conf он автовыпиливается (используется tinydancer-noop)

## bugs

- [x] при повороте экрана все имена линий становятся "#3" (имя последней линии последнего графика)
  - https://stackoverflow.com/questions/2512010/android-checkbox-restoring-state-after-screen-rotation
- [x] в последнем значении в cursorPopup срабатывает перенос слов
  - hack - можно сделать в разметке cursor_popup 2 ряда LinearLayout - cursor_values_1 и cursor_values_2
- [x] стабильные даты по X при изменении масштаба
- [x] картинка mode_icon в разных размерах, или svg (material.io)
  - won't fix. странный засвет при нажатии только на v16
- [x] при старте не нужна первая анимация onZoneChanged
- [x] зоны TOUCH_SLOP
  - увеличить зону TOUCH_SLOP2, когда граница находится у краёв экрана
  - левая "наезжает" на правую
  - левая "наезжает" на центр

## low
- [ ] k/kk при выводе значений больше 1000/1000000 по Y?
- [x] какой yMin использовать на графике?
  - сейчас ~~всегда 0~~ ~~минимум в диапазоне~~ всегда 0 - так на ref
- [x] немного добавлять к yMax? (часть размаха)
  - добавляем часть размаха
- [x] размер checkbox в списке имён сигналов
  - https://stackoverflow.com/questions/2151241/android-how-to-change-checkbox-size
  - won't fix. оставляем стандартный checkbox
- [x] увеличить отступ между checkbox и его текстом
  - https://stackoverflow.com/questions/4037795/android-spacing-between-checkbox-and-text
- [x] первым делом проверять на попадание в зону MoveMode.LEFT_AND_RIGHT
- [ ] performClick? ripple при тапе?
- [x] rtl языки
  - адаптировал только положение значений оцифровки оси Y. Течение времени не зеркалим (https://material.io/design/usability/bidirectionality.html#mirroring-elements)
- [x] не блокировать скроллинг у родителя (requestDisallowInterceptTouchEvent), если начальное перемещение было по вертикали
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
- [ ] асинхронная загрузка данных из chart_data.json
- [ ] MVC? чтобы "выживать" при смене ориентации экрана
- [ ] комментарии на eng
- [x] внести xMin/yMin в xToPixel/yToPixel (проверить pixelToX)

# TODO v2

## high

performance!

- main
  - [x] y_scaled  
        когда включены обе линии оцифровка справа приближённая ))
  - [x] K/M при выводе значений больше 1000/1000000 по Y  
        использованное решение игнорирует локаль - разделитель всегда точка
  - [x] не выводить значения оцифровки, частично выходящие за границы экрана

- preview
  - [x] дизайн левой/правой границ зоны
  - [x] скругленные границы зоны
  - [ ] скругленные границы всего превью
  - [x] зона больше всего превью по высоте

- список линий
  - [x] скруглённые кнопки

- плашка
  - [ ] x-положение (по центру, не должна закрывать верхнюю точку)
  - [x] иконка перемещения курсора вправо
  - [x] y-значения с тысячными разделителями (4 657 345)
  - [x] скрывать при отсутствии линий (остаётся только дата)
  - [x] не обрабатывать onCursorChanged при отсутствии линий
  - [x] граница (elevation) - _won't fix_

- misc
  - [x] иконка light\dark
  - [x] через линии сигналов просвечивают у-линии оцифровки
  - [ ] графики отрисовываются на всю ширину, выходя за границы вида/оцифровки

- анимация
  - [ ] смена названия графика и "zoom out"
  - [ ] смена выбранного диапазона при зуме
  - [ ] перемещение курсора вправо (используя уравнение линии)
  - [ ] изменение значений на плашке

## bugs

- [x] плашка: иногда значения не выравниваются по правому краю
- [x] плашка: выравнивание значений в процентах
- [x] preview: сигналы отрисовываются за границами скругления  
      пока отрисовываем без скругления
- [ ] курсор при перемещении к последней точке сбрасывается в начало сигнала
- [x] деление на 0 в updateYAxisMarks `int startYValue = yMin / stepValue * stepValue` при изменении зоны и отсутствии сигналов
- [x] при прерывании/быстром переключении фильтрации остаются линии - нужно сбрасывать состояние видимости?
- [x] BAR: выделение цветом выбранного столбца
- [x] BAR: прямоугольники, а не трапеции - столбцы одинаковой ширины
- [x] BAR: последний столбец в диапазоне за границей экрана?  
      Да. Сейчас столбец выводится (только в методах updateLines_BAR_Path_Matrix и updateCursorPaths) по центру точки/надписи (со смещением влево на половину ширины). Но остаётся проблема - крайние столбцы выводятся только в половинную ширину.

## low

- [ ] можно ли использовать SHORT dateformat для даты курсора?
- [ ] цвета графиков/кнопок/значений курсора на основе спецификации (захардкодить)
- [x] самостоятельная отрисовка плашки (просто drawText на канве графика, без использования View)
