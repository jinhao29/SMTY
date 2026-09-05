package com.shangmentiyu.sportscoach.data.repo

import com.shangmentiyu.sportscoach.data.db.CoachDao
import com.shangmentiyu.sportscoach.data.model.Coach
import com.shangmentiyu.sportscoach.data.model.CoachRole
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull

/**
 * 团队树节点：教练 + 直接下属（递归结构，合伙人层级展示用）。
 *
 * @param coach 教练档案
 * @param children 直接下属节点（按 superiorId 递归组装）
 */
data class CoachTreeNode(
    val coach: Coach,
    val children: List<CoachTreeNode>
)

/**
 * 教练 Repository（管理层）。
 *
 * 从原 [OperationRepository] 拆分而来，单一职责只管理教练数据。
 *
 * 职责：
 * - 教练 CRUD
 * - 按"在职/全部/角色"过滤查询
 * - 合伙人团队树组装 / 成员转让（防环校验）
 *
 * 设计说明：
 * - 教练数据量小（通常 ≤10），不引入分页
 * - 团队树在内存组装（每层一次查询改为一次性拉全量后建索引，避免 N+1）
 */
class CoachRepository(private val coachDao: CoachDao) {

    /** 在职教练列表（按姓名升序），用于下拉选择与排课分配 */
    fun getActiveCoaches(): Flow<List<Coach>> = coachDao.getActive()

    /** 全量教练列表（含已离职），用于历史报表 / 设置页管理 */
    fun getAllCoaches(): Flow<List<Coach>> = coachDao.getAll()

    /** 按角色查在职教练（合伙人层级 / 团队管理） */
    fun getCoachesByRole(role: String): Flow<List<Coach>> = coachDao.getByRole(role)

    /** 查某教练的直接下属（离职/删除前校验、团队管理用） */
    fun getCoachesBySuperior(superiorName: String): Flow<List<Coach>> = coachDao.getBySuperior(superiorName)

    /** 按姓名查教练；找不到返回 null */
    suspend fun getByName(name: String): Coach? = coachDao.getByName(name)

    /**
     * 新增或更新教练（按主键 name REPLACE）。
     *
     * 保存前做上级合法性校验：
     * - 上级必须存在且在职
     * - 上级角色层级必须高于自己（L1 > L2 > 全职/兼职）
     * - 不允许把自己设为自己的上级（环路由 transferMembers 统一防护）
     *
     * @throws IllegalArgumentException 校验失败时抛出，message 为用户可读文案
     */
    suspend fun upsert(coach: Coach) {
        validateSuperior(coach)
        coachDao.upsert(coach)
    }

    /** 按姓名物理删除教练（仅在教练离职且无历史排课时使用） */
    suspend fun delete(name: String) = coachDao.deleteByName(name)

    /**
     * 组装完整团队树：根节点为一级合伙人 + 无上级的其他教练。
     *
     * 层级约定：一级合伙人 → 二级合伙人 → 全职/兼职教练。
     * 数据异常（上级不存在 / 成环）时该教练挂到根层级展示，保证树不丢人。
     */
    suspend fun buildTeamTree(): List<CoachTreeNode> {
        val all = coachDao.getAll().firstOrNullList()
        val byName = all.associateBy { it.name }
        val childrenOf = all.groupBy { it.superiorId }

        fun buildNode(coach: Coach, visited: Set<String>): CoachTreeNode =
            CoachTreeNode(
                coach = coach,
                children = (childrenOf[coach.name] ?: emptyList())
                    .filter { it.name !in visited }
                    .map { buildNode(it, visited + it.name) }
            )

        val roots = all.filter { node ->
            val superior = node.superiorId?.let { byName[it] }
            superior == null || wouldCycle(all, node, node.name)
        }
        return roots
            .sortedWith(compareByDescending<Coach> { it.role == CoachRole.PARTNER_L1 }.thenBy { it.name })
            .map { buildNode(it, setOf(it.name)) }
    }

    /**
     * 转让团队成员：把 [fromName] 名下全部直接下属整体转给 [toName]。
     *
     * 校验：
     * - 双方教练必须存在
     * - to 不得在 from 的下属子树内（否则成环）
     * - 角色层级：to 必须能管理这些成员（to 的层级 ≥ from 的层级）
     *
     * @throws IllegalArgumentException 校验失败时抛出，message 为用户可读文案
     */
    suspend fun transferMembers(fromName: String, toName: String) {
        if (fromName == toName) throw IllegalArgumentException("不能转让给自己")
        val all = coachDao.getAll().firstOrNullList()
        val from = all.find { it.name == fromName }
            ?: throw IllegalArgumentException("原上级「$fromName」不存在")
        val to = all.find { it.name == toName }
            ?: throw IllegalArgumentException("新上级「$toName」不存在")

        val members = all.filter { it.superiorId == fromName }
        if (members.isEmpty()) throw IllegalArgumentException("「$fromName」名下没有可转让的成员")

        // 环检测：to 若在 from 的子树中，转让后 from 会成为 to 的后代 → 成环
        val subtree = collectSubtreeNames(all, fromName)
        if (toName in subtree) throw IllegalArgumentException("「$toName」是「$fromName」的下属，转让后层级成环")

        members.forEach { member ->
            coachDao.upsert(member.copy(superiorId = toName))
        }
    }

    /** Flow 一次性取值辅助（空 Flow 返回空列表） */
    private suspend fun <T> Flow<List<T>>.firstOrNullList(): List<T> =
        this.firstOrNull() ?: emptyList()

    /** superiorId 链上是否从 name 出发会回到自身（数据修复用判环） */
    private fun wouldCycle(all: List<Coach>, start: Coach, target: String): Boolean {
        var current: Coach? = start
        var steps = 0
        while (current?.superiorId != null && steps < all.size + 1) {
            if (current.superiorId == target) return true
            current = all.find { it.name == current?.superiorId }
            steps++
        }
        return false
    }

    /** 收集 name 的全部后代姓名（含自身） */
    private fun collectSubtreeNames(all: List<Coach>, name: String): Set<String> {
        val childrenOf = all.groupBy { it.superiorId }
        val result = mutableSetOf(name)
        val queue = ArrayDeque(listOf(name))
        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            childrenOf[current]?.forEach { child ->
                if (child.name !in result) {
                    result.add(child.name)
                    queue.add(child.name)
                }
            }
        }
        return result
    }

    /** 上级合法性校验（存在 + 在职 + 层级更高） */
    private suspend fun validateSuperior(coach: Coach) {
        val superiorName = coach.superiorId ?: return
        if (superiorName.isBlank()) return
        if (superiorName == coach.name) {
            throw IllegalArgumentException("上级不能是自己")
        }
        val superior = coachDao.getByName(superiorName)
            ?: throw IllegalArgumentException("上级「$superiorName」不存在")
        if (superior.status != "在职") {
            throw IllegalArgumentException("上级「${superiorName}」当前为${superior.status}状态，请先更换上级")
        }
        if (CoachRole.rank(superior.role) <= CoachRole.rank(coach.role)) {
            throw IllegalArgumentException(
                "上级「$superiorName」（${CoachRole.label(superior.role)}）层级不高于自己（${CoachRole.label(coach.role)}）"
            )
        }
    }
}
