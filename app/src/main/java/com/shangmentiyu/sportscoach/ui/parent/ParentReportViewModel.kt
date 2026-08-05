package com.shangmentiyu.sportscoach.ui.parent

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shangmentiyu.sportscoach.core.ReportGenerator
import com.shangmentiyu.sportscoach.data.model.ParentReport
import com.shangmentiyu.sportscoach.data.model.Student
import com.shangmentiyu.sportscoach.data.repo.ParentReportRepository
import com.shangmentiyu.sportscoach.data.repo.StudentRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 家长报告协调层：管理报告生成、列表筛选、分享状态。
 */
class ParentReportViewModel(
    private val reportRepo: ParentReportRepository,
    private val studentRepo: StudentRepository
) : ViewModel() {

    /** 全部学员 */
    val students: StateFlow<List<Student>> = studentRepo.getAllStudents()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** 全部报告（按生成时间倒序） */
    val reports: StateFlow<List<ParentReport>> = reportRepo.getAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** 当前选中的学员（null = 全部） */
    private val _selectedStudent = MutableStateFlow<String?>(null)
    val selectedStudent: StateFlow<String?> = _selectedStudent.asStateFlow()

    /** 当前选中的报告（用于查看详情） */
    private val _viewingReport = MutableStateFlow<ParentReport?>(null)
    val viewingReport: StateFlow<ParentReport?> = _viewingReport.asStateFlow()

    /** 操作结果提示 */
    private val _toast = MutableStateFlow<String?>(null)
    val toast: StateFlow<String?> = _toast.asStateFlow()
    private val appExceptionHandler =
        com.shangmentiyu.sportscoach.core.CoroutineExt.createAppExceptionHandler(_toast, "ParentReportViewModel")

    fun selectStudent(name: String?) {
        _selectedStudent.value = name
    }

    fun viewReport(report: ParentReport) {
        _viewingReport.value = report
    }

    fun clearViewing() {
        _viewingReport.value = null
    }

    fun consumeToast() {
        _toast.value = null
    }

    /** 生成报告 */
    fun generateReport(studentName: String, type: String) {
        viewModelScope.launch(appExceptionHandler) {
            try {
                val id = reportRepo.generateAndSave(studentName, type)
                if (id != null) {
                    _toast.value = "$type 已生成"
                } else {
                    _toast.value = "生成失败：学员不存在、无课时记录或本周期已生成过"
                }
            } catch (e: Exception) {
                _toast.value = "生成失败：${e.message}"
            }
        }
    }

    /** 标记已分享 */
    fun markShared(reportId: String) {
        viewModelScope.launch(appExceptionHandler) {
            reportRepo.markShared(reportId)
            _toast.value = "已标记为已分享"
        }
    }

    /** 删除报告 */
    fun deleteReport(reportId: String) {
        viewModelScope.launch(appExceptionHandler) {
            reportRepo.delete(reportId)
            _toast.value = "报告已删除"
            if (_viewingReport.value?.id == reportId) {
                _viewingReport.value = null
            }
        }
    }

    /** 按当前选中筛选学员返回的报告列表 */
    fun filteredReports(all: List<ParentReport>): List<ParentReport> {
        val name = _selectedStudent.value ?: return all
        return all.filter { it.studentName == name }
    }
}
