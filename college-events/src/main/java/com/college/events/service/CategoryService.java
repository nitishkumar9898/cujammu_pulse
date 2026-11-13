package com.college.events.service;

import com.college.events.model.Category;
import com.college.events.repository.CategoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@SuppressWarnings("null")
public class CategoryService {

    private final CategoryRepository categoryRepository;

    public List<Category> findAll() {
        return categoryRepository.findAll();
    }

    public Optional<Category> findById(Long id) {
        return categoryRepository.findById(id);
    }

    @Transactional
    public Category save(String name, String icon, String colorClass) {
        if (categoryRepository.existsByName(name)) {
            throw new IllegalArgumentException("Category already exists: " + name);
        }
        return categoryRepository.save(Category.builder()
            .name(name).icon(icon).colorClass(colorClass).build());
    }

    @Transactional
    public void delete(Long id) {
        categoryRepository.deleteById(id);
    }
}
