package com.sync.repository;

import com.sync.domain.expense.ExpenseMember;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ExpenseMemberRepository extends JpaRepository<ExpenseMember, Long> {

    List<ExpenseMember> findByExpenseId(Long expenseId);

    void deleteByExpenseId(Long expenseId);
}