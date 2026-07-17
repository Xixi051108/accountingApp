Page({
  data: {
    weeks: ['日', '一', '二', '三', '四', '五', '六'],
    currentYear: new Date().getFullYear(),
    currentMonth: new Date().getMonth() + 1,
    daysInMonth: [],
    emptyDays: [],
    selectedDate: '',
    selectedDateText: '',
    selectedDiaries: [],
    moodLevels: ['很好', '一般', '不好'],
    moodLevelIndex: 0,
    diaryContent: ''
  },

  onShow() {
    this.initCalendar();
  },

  pad(num) {
    num = Number(num);
    return num < 10 ? `0${num}` : String(num);
  },

  formatDateKey(date) {
    return `${date.getFullYear()}-${this.pad(date.getMonth() + 1)}-${this.pad(date.getDate())}`;
  },

  formatDateText(dateKey) {
    const [year, month, day] = dateKey.split('-');
    return `${year}年${Number(month)}月${Number(day)}日`;
  },

  getMonthDateKey(day) {
    return `${this.data.currentYear}-${this.pad(this.data.currentMonth)}-${this.pad(day)}`;
  },

  getMoodClass(mood) {
    const map = {
      '很好': 'mood-good',
      '一般': 'mood-normal',
      '不好': 'mood-bad'
    };
    return map[mood] || '';
  },

  makeEmptyDays(count) {
    const list = [];
    for (let i = 0; i < count; i++) {
      list.push({ id: `empty-${i}` });
    }
    return list;
  },

  findMoodRecord(list, dateKey) {
    for (let i = 0; i < list.length; i++) {
      if (list[i].dateKey === dateKey) {
        return list[i];
      }
    }
    return null;
  },

  initCalendar() {
    const { currentYear, currentMonth } = this.data;
    const firstDay = new Date(currentYear, currentMonth - 1, 1).getDay();
    const totalDays = new Date(currentYear, currentMonth, 0).getDate();
    const moodList = wx.getStorageSync('moodList') || [];
    const todayKey = this.formatDateKey(new Date());
    const selectedDate = this.data.selectedDate || todayKey;

    const days = [];
    for (let day = 1; day <= totalDays; day++) {
      const dateKey = this.getMonthDateKey(day);
      const diaryRecord = this.findMoodRecord(moodList, dateKey);
      const moodType = diaryRecord ? diaryRecord.mood : '';

      days.push({
        day,
        fullDate: dateKey,
        isToday: dateKey === todayKey,
        hasMood: !!diaryRecord,
        moodClass: this.getMoodClass(moodType)
      });
    }

    this.setData({
      emptyDays: this.makeEmptyDays(firstDay),
      daysInMonth: days,
      selectedDate,
      selectedDateText: this.formatDateText(selectedDate)
    });
    this.filterDiaries(selectedDate);
  },

  prevMonth() {
    let { currentYear, currentMonth } = this.data;
    currentMonth -= 1;
    if (currentMonth < 1) {
      currentYear -= 1;
      currentMonth = 12;
    }
    this.setData({ currentYear, currentMonth, selectedDate: '' });
    this.initCalendar();
  },

  nextMonth() {
    let { currentYear, currentMonth } = this.data;
    currentMonth += 1;
    if (currentMonth > 12) {
      currentYear += 1;
      currentMonth = 1;
    }
    this.setData({ currentYear, currentMonth, selectedDate: '' });
    this.initCalendar();
  },

  selectDate(e) {
    const date = e.currentTarget.dataset.date;
    this.setData({
      selectedDate: date,
      selectedDateText: this.formatDateText(date)
    });
    this.filterDiaries(date);
  },

  moodLevelChange(e) {
    this.setData({ moodLevelIndex: Number(e.detail.value) });
  },

  inputDiaryContent(e) {
    this.setData({ diaryContent: e.detail.value });
  },

  saveDiary() {
    const content = String(this.data.diaryContent || '').replace(/^\s+|\s+$/g, '');
    if (!content) {
      wx.showToast({ title: '先写点日记内容吧', icon: 'none' });
      return;
    }

    const now = Date.now();
    const moodList = wx.getStorageSync('moodList') || [];
    const newDiary = {
      id: now,
      dateKey: this.data.selectedDate,
      dateText: this.data.selectedDateText,
      mood: this.data.moodLevels[this.data.moodLevelIndex],
      content,
      timestamp: now
    };

    moodList.unshift(newDiary);
    wx.setStorageSync('moodList', moodList);

    this.setData({
      diaryContent: '',
      moodLevelIndex: 0
    });
    wx.showToast({ title: '日记已保存', icon: 'success' });
    this.initCalendar();
  },

  filterDiaries(date) {
    const moodList = wx.getStorageSync('moodList') || [];

    const freeDiaries = [];
    for (let i = 0; i < moodList.length; i++) {
      const item = moodList[i];
      if (item.dateKey === date) {
        freeDiaries.push({
        id: `diary-${item.id}`,
        mood: item.mood,
        content: item.content,
        meta: item.dateText || this.formatDateText(item.dateKey),
        timestamp: item.timestamp || item.id
        });
      }
    }

    const selectedDiaries = freeDiaries.sort((a, b) => b.timestamp - a.timestamp);
    for (let i = 0; i < selectedDiaries.length; i++) {
      selectedDiaries[i].pageNo = i + 1;
    }
    this.setData({ selectedDiaries });
  }
});
